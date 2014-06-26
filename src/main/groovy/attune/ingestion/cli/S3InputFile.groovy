package attune.ingestion.cli

import groovy.transform.Canonical
import org.apache.commons.codec.digest.DigestUtils

@Canonical
class S3InputFile {
    String resource
    String id
    String uploadUrl
    String localPath
    String encryptionKey

    String getEncryptionKeyMd5() {
        if (!encryptionKey) {
            return null
        }
        byte[] decodedEncryptionKey = encryptionKey.decodeBase64()
        DigestUtils.md5(decodedEncryptionKey).encodeBase64().toString()
    }

}