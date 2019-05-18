#  Getting-Start sample java client of HyperLedger Indy

## Prerequisite and Installs

### Tested environment

```console

$ cat /etc/os-release 
  NAME="Ubuntu"
  VERSION="16.04.5 LTS (Xenial Xerus)"
  ID=ubuntu
  ID_LIKE=debian
  PRETTY_NAME="Ubuntu 16.04.5 LTS"
  VERSION_ID="16.04"
  HOME_URL="http://www.ubuntu.com/"
  SUPPORT_URL="http://help.ubuntu.com/"
  BUG_REPORT_URL="http://bugs.launchpad.net/ubuntu/"
  VERSION_CODENAME=xenial
  UBUNTU_CODENAME=xenial

$ uname -ra
 Linux myhost 4.4.0-137-generic #163-Ubuntu SMP Mon Sep 24 13:14:43 UTC 2018 x86_64 x86_64 x86_64 GNU/Linux
 
$ docker --version
  Docker version 18.06.1-ce, build e68fc7a
  
$ java -version 
  java version "1.8.0_172"
  Java(TM) SE Runtime Environment (build 1.8.0_172-b11)
  Java HotSpot(TM) 64-Bit Server VM (build 25.172-b11, mixed mode)   
  
$ mvn -version
  Apache Maven 3.5.4 (1edded0938998edf8bf061f1ceb3cfdeccf443fe; 2018-06-18T02:33:14+08:00)
  Maven home: /usr/local/apache-maven-3.5.4
  Java version: 1.8.0_171, vendor: Oracle Corporation, runtime: /usr/local/java/jdk1.8.0_171/jre
  Default locale: zh_CN, platform encoding: UTF-8
  OS name: "linux", version: "4.15.0-47-generic", arch: "amd64", family: "unix"
  
$ cargo -version
  cargo 1.34.0 (6789d8a0a 2019-04-01)
  
```

### Start local docker test nodes

```console
$ git clone https://github.com/zixian5/indy-sdk-javaDemo.git

$ cd indy-sdk-javaDemo

$ docker build -f ci/indy-pool.dockerfile -t indy_pool .

$ docker run -itd -p 9701-9708:9701-9708 indy_pool

$ docker run --name some-postgres -e POSTGRES_PASSWORD=mysecretpassword -d -p 5432:5432 postgres -c 'log_statement=all' -c 'logging_collector=on' -c 'log_destination=stderr'
```

If successful, then `$ docker ps -a` should show the running container.

### Install Indy Librarires

Build each project (indy-sdk/libindy, indy-sdk/cli, indy-sdk/experimental/plugins/postgres_storage):
```console
$ cd <to each project directory>

$ cargo build

```
Copy every library to the folder `/usr/lib`
```console
$ sudo cp libindy/target/debug/libindy.so /usr/lib

$ sudo cp experimental/plugins/postgres_storage/target/debug/libindystrgpostgres.so /usr/lib

```
If successful, then the `/usr/lib/libindy.so` file should be installed locally.

## Run this sample application

Under this folder, run

```console
$ export RUST_LOG=trace
$ export LD_LIBRARY_PATH=/usr/lib/
$ mvn exec:java -Dexec.mainClass=Main
```

## References

https://github.com/hyperledger/indy-sdk/blob/master/README.md

https://github.com/hyperledger/indy-sdk/tree/master/wrappers/java

https://github.com/hyperledger/indy-sdk/tree/master/doc/how-tos

https://github.com/blokaly/indy-java-cli/tree/master/src/main
