package attune.ingestion.cli

import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.model.ObjectMetadata
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.CliBuilder
import groovyx.net.http.HTTPBuilder
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.apache.commons.codec.digest.DigestUtils
import org.rauschig.jarchivelib.Compressor
import org.rauschig.jarchivelib.CompressorFactory
import org.rauschig.jarchivelib.CompressionType

import static groovyx.gpars.GParsPool.withPool
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

class AttuneUpload {
    String host
    String oauthKey
    String version
    String tag
    def typeFilePairs
    String compression

    private KeyGenerator generator = KeyGenerator.getInstance("AES")
    private HTTPBuilder http

    static void main(args) {
        def cli = new CliBuilder(usage: 'attuneUpload [options] resource file ...')
        cli.with {
            h longOpt: 'help', 'display this message'
            b args: 1, longOpt: 'host', 'hostname to connect to, default attune staging'
            c args: 1, longOpt: 'compression', 'compression format of file, will gzip file if unspecified; specify \'none\' to skip compression'
            g args: 1, 'generation id for this transaction, random UUID used if not provided'
            o args: 1, longOpt: 'oauth', 'oauth bearer token for authentication', required: true
            v args: 1, longOpt: 'version', 'api version, default v1'
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        if (options.h) {
            cli.usage()
            return
        }

        def attuneUpload = new AttuneUpload()
        attuneUpload.host = options.b ?: 'https://data.attune-staging.co'

        attuneUpload.oauthKey = options.o
        attuneUpload.version = options.g ?: UUID.randomUUID() as String

        attuneUpload.tag = options.v ?: 'v1'
        attuneUpload.compression = options.c ?: null

        if (!options.arguments()) {
            fail 'No files specified'
        }

        if ((options.arguments().size() % 2) != 0) {
            fail 'Found odd number of arguments, even number required'
        }

        attuneUpload.typeFilePairs = options.arguments().collate(2)
        attuneUpload.run()
    }

    static fail(String message) {
        println "FAILURE: ${message}"
        System.exit(1)
    }

    void run() {
        http = new HTTPBuilder( host )
        http.headers = [Authorization: "Bearer ${oauthKey}"]

        generator.init(256, new SecureRandom())

        withPool {
            def s3Files = typeFilePairs.collectParallel { pair ->
                uploadFile(pair)
            }

            def requests = s3Files.collect { s3File ->
                 [relUrl: "${tag}/${s3File.resource}/many?g=${version}".toString(),
                        method: 'PUT', fileId: s3File.id]
            }
            requests << [
                relUrl: "${tag}/entities/activeGeneration".toString(),
                method: 'PUT',
                body: JsonOutput.toJson([generation: version])
            ]
            def batchRequest = [process: 'sequential', requests: requests]

            http.request(POST,JSON) {
                uri.path = '/batch'
                body = batchRequest

                response.success = { resp, json ->
                    processBatchResponse(json, s3Files)
                }
                response.failure = { resp, json ->
                    println 'Batch submit failure'
                    fail json.errorMessage
                }
            }
        }
    }

    private S3InputFile uploadFile(List typeFilePair) {
        S3InputFile s3File = new S3InputFile(resource: typeFilePair[0], localPath: typeFilePair[1])
        String key = generator.generateKey().encoded.encodeBase64().toString()
        s3File.encryptionKey = generator.generateKey().encoded.encodeBase64().toString()

        File file = new File(s3File.localPath)
        if (!file.exists()) {
            fail "File ${s3File.localPath} does not exist!"
        }
        String effectiveCompression = 'gzip'
        if (compression) {
            effectiveCompression = compression == 'none' ? null : compression
        } else {
            println "Creating gzip for ${file.path}"
            file = gzipFile(file)
        }
        byte[] md5Bytes = DigestUtils.md5(new FileInputStream(file))
        String md5 = md5Bytes.encodeBase64().toString()

        http.request(POST,JSON) {
            uri.path = '/s3Input'
            body = [md5: md5, encryptionKey: s3File.encryptionKey, compression: effectiveCompression]

            response.success = { resp, json ->
                s3File.id = json.id
                s3File.uploadUrl = json.uploadUrl
            }
            response.failure = { resp, json ->
                String error = json?.errorMessage ?: resp.status
                fail "Failure uploading file ${s3File.localPath} for ${s3File.resource} with error ${error}"
            }
        }
        performUpload(s3File, file, md5)
        s3File
    }

    private void performUpload(s3File, file, md5) {

        println "Uploading file ${s3File.localPath} for ${s3File.resource} with id ${s3File.id}"

        URL url = new URL(s3File.uploadUrl)

        def connection= url.openConnection()
        connection.doOutput = true
        connection.requestMethod = "PUT"
        connection.fixedLengthStreamingMode = file.length()
        connection.setRequestProperty(Headers.CONTENT_MD5, md5)
        connection.setRequestProperty(Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM, ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
        connection.setRequestProperty(Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY, s3File.encryptionKey)
        connection.setRequestProperty(Headers.SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5, s3File.encryptionKeyMd5)

        writeToConnection(s3File, file, connection)
        println("File  ${s3File.localPath} for ${s3File.resource} upload completed, checking result")
        int responseCode = connection.responseCode

        if (responseCode == 200) {
            println("File ${s3File.localPath} for ${s3File.resource} successfully uploaded.")
        } else {
            Scanner s = new Scanner(connection.getErrorStream())
            s.useDelimiter("\\Z")
            fail "Error uploading  ${s3File.localPath} for ${s3File.resource}: ${s.next()}"
        }
    }

    private void writeToConnection(s3File, file, connection) {
        OutputStream out = connection.getOutputStream()
        InputStream inputStream = new FileInputStream(file)

        byte[] buf = new byte[16384]
        int count
        int total = 0
        long fileSize = file.length()
        int logThreshold = 10

        while ((count =inputStream.read(buf)) != -1)
        {
            out.write(buf, 0, count)
            total += count
            int pctComplete = (total / fileSize) * 100

            if (pctComplete >= logThreshold) {
                println "File ${s3File.localPath} for ${s3File.resource} is ${pctComplete}% uploaded (${total} of ${fileSize} bytes)"
                logThreshold += 10
            }
        }
        out.close()
        inputStream.close()
    }

    private processBatchResponse(json, s3Files) {
        println "Batch for generation ${version} submitted. Results:"
        s3Files.eachWithIndex { s3File, index ->
            int code = json.responses[index].code
            if (code == 202) {
                println "File ${s3File.localPath} for ${s3File.resource} submitted sucessfully"
            } else {
                println "File ${s3File.localPath} for ${s3File.resource} failed submission"
                JsonSlurper jsonSlurper = new JsonSlurper()
                def parsedBody = jsonSlurper.parseText(json.responses[index].body)
                fail "Error is ${parsedBody.errorMessage}"
            }
        }
        int code = json.responses[s3Files.size()].code
        if (code == 202) {
            println "Finalization of generation ${version} success."
        } else {
            println "Finalization of generation ${version} failed"
            JsonSlurper jsonSlurper = new JsonSlurper()
            def parsedBody = jsonSlurper.parseText(json.responses[index].body)
            fail "Error is ${parsedBody.errorMessage}"
        }
    }

    private File gzipFile(file) {
        Compressor compressor = CompressorFactory.createCompressor(CompressionType.GZIP)
        File dest = new File(file.parent, file.name + '.gz')
        compressor.compress(file, dest)
        dest
    }
}