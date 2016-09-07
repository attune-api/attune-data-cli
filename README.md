# AttuneUpload
AttuneUpload is a command line utility that can be used to upload files to Attune.  AttuneUpload copies the files to Attune's AWS S3 bucket and trigger's their processing by
invoking Attune's data processing API.  It supports compressing and encrypting file before upload.  AttuneUpload runs on Java and therefore can be deployed on a wide variety
of machine architectures.

### Installation
#### Prerequisites
AttuneUpload requires Java to be pre-installed.  It runs on JDK 6 or above.
AttuneUpload can be installed on Windows or Unix/Linux servers.
The server should be able to reach Amazon AWS and the Attune data api (https://data-api.attune.co) which is deployed on Amazon EC2.

#### Install Steps
AttuneUpload is delivered as a zip archive.  To install, simply unzip the archive to a convenient location.  The installation requires roughly 35MB free disk space.
Ensure that the user running AttuneUpload is able to run Java.  This requires that the JAVA_HOME environment variable is set and the java executable is in the path.

### Usage

```
> ./attuneUpload --help
error: Missing required option: o
usage: attuneUpload [options] resource file ...
 -b,--host <arg>          hostname to connect to, default attune staging
 -c,--compression <arg>   compression format of file, will gzip file if
                          unspecified; specify 'none' to skip compression
 -g <arg>                 generation id for this transaction, random UUID
                          used if not provided
 -h,--help                display this message
 -o,--oauth <arg>         oauth bearer token for authentication
 -v,--version <arg>       api version, default v1
 resource                 the type of data contained in the file
 file                     the file containing the actual data to be upload
 
One or more resource file pairs may be specified.
```

### Sample call

```
> ./attuneUpload -o b92f247f-345b-4e96-bc68b-a1bce47024a entities/orders /data/Downloads/orders.json entities/sales /data/Downloads/sales.json

Creating gzip for /data/Downloads/orders.json
Creating gzip for /data/Downloads/sales.json
Uploading file /data/Downloads/orders.json for entities/orders with id 61e2c923-c1a3-4352-9343-9f4678e367df
Uploading file /data/Downloads/sales.json for entities/sales with id 49b38cd0-022d-4fac-b9d9-07d1a9bc0b4b
File /data/Downloads/orders.json for entities/orders successfully uploaded.
File /data/Downloads/sales.json for entities/sales successfully uploaded.
Batch for generation dbc9cb57-9a50-437c-b483-308ab74a7afc submitted. Results:
File /data/Downloads/orders.json for entities/orders submitted sucessfully
File /data/Downloads/sales.json for entities/sales submitted sucessfully
Finalization of generation dbc9cb57-9a50-437c-b483-308ab74a7afc success.
```

### Building

Uses the [Gradle application plugin](http://www.gradle.org/docs/current/userguide/application_plugin.html) for generating distributions.

```
> ./gradle clean distZip
```
