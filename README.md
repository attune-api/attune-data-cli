Command line utility for uploading multiple files to Attune in parallel and submitting them as a single generation.

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
```

### Sample call

```
> ./attuneUpload -o d52f247f-34ab-4e56-bb8b-3c87cfbc024a entities/orders /data/Downloads/orders.json entities/sales /data/Downloads/sales.json

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
