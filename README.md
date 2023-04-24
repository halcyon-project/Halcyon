# Halcyon

 https://arxiv.org/abs/2304.10612

**adjective:**<br>
&nbsp;&nbsp;&nbsp;&nbsp;denoting a period of time in the past that was idyllically happy and peaceful.<br>
&nbsp;&nbsp;&nbsp;&nbsp;"the halcyon days of the mid-1980s, when profits were soaring"<br>

&nbsp;&nbsp;&nbsp;&nbsp;**Similar** - serene, calm, pleasant, balmy, tranquil, peaceful, temperate, mild, quiet, gentle, placid, still, windless, stormless, happy, carefree, blissful, golden, joyful, joyous, contented, idyllic, palmy, flourishing, thriving, prosperous, successful<br>
&nbsp;&nbsp;&nbsp;&nbsp;**Opposite** - stormy, troubled<br>

**noun:**<br>
&nbsp;&nbsp;&nbsp;&nbsp;1. a tropical Asian and African kingfisher with brightly colored plumage.<br>
&nbsp;&nbsp;&nbsp;&nbsp;2. a mythical bird said by ancient writers to breed in a nest floating at sea at the winter solstice, charming the wind and waves into calm.<br>
&nbsp;&nbsp;&nbsp;&nbsp;3. The name of a software system for managing Pathology Whole Slide Images and derived features from AI pipelines.<br>

## Building....

```sh
ingest - command line tool  (this will require a functioning GraalVM native image environment - https://www.graalvm.org/22.3/reference-manual/native-image/)
mvn -Pingest clean package 

halcyon jar version.....
mvn -Pserver clean package
```

## jpackage Packaging (Windows)

```sh
mvn -Pserver clean package
mvn -Pwindows-installer jpackage:jpackage
```

## SSL:

[How to enable HTTPS in a Spring Boot Java application](https://www.thomasvitale.com/https-spring-boot-ssl-certificate/)

```sh
keytool -genkeypair -alias halcyon -keyalg RSA -keysize 4096 -storetype PKCS12 -keystore halcyon.p12 -validity 3650 -storepass password
```

### Private key (seems the same as above, will test)

```sh
keytool -genkey -keyalg RSA -alias selfsigned -keystore halcyon.jks -storepass password -validity 360 -keysize 2048
```

### See also:

[How set up Spring Boot to run HTTPS / HTTP ports](https://stackoverflow.com/questions/30896234/how-set-up-spring-boot-to-run-https-http-ports/49740689)


## Migrations to p12

```sh
keytool -importkeystore -srckeystore cacerts -destkeystore cacerts -deststoretype pkcs12

keytool -delete -noprompt -alias halcyon  -keystore cacerts.p12 -storepass changeit
```

### Export key:

```sh
keytool -export -keystore cacerts.p12 -alias halcyon -file halcyon.cer
```

### Import key

```sh
keytool -import -alias halcyon -keystore C:\bin\graalvm\lib\security\cacerts -file erich-bremer.pem
```

### Convert apache key to right format:

```sh
openssl pkcs12 -export -in [<em>filename-certificate</em>] -inkey [<em>filename-key</em>] -name [<em>host</em>] -out [<em>filename-new</em>-PKCS-12.p12]
```

[Import an existing SSL certificate and private key for Wowza Streaming Engine](https://www.wowza.com/docs/how-to-import-an-existing-ssl-certificate-and-private-key#:~:text=You%20can't%20directly%20import,12%20file%20into%20your%20keystore.)


## Importing new/renewal PEM certs into certificate store

```sh
openssl pkcs12 -export -in wow.fullchain -inkey atoz2022.key  -name shared > server.p12

keytool -importkeystore -srckeystore server.p12 -destkeystore cacerts.p12 -srcstoretype pkcs12 -alias shared
```

## Extra

```sh
keytool -import -alias sbu -keystore cacerts.p12 -trustcacerts -file inter.crt
keytool -list -v -keystore cacerts.p12
keytool -list -v -keystore cacerts.p12
keytool -list -v -keystore cacerts.p12 | more
keytool -import -alias sbu -keystore cacerts.p12 -trustcacerts -file inter.crt
keytool -import -alias sbu -keystore /home/bremer/graalvm/lib/security/cacerts -trustcacerts -file inter.crt
keytool -list -v -keystore cacerts.p12
keytool -list -v -keystore cacerts.p12 | grep alias
keytool -list -v -keystore cacerts.p12 | grep Alias
keytool -list -v -keystore cacerts.p12 | grep Alias | more
keytool -list -v -keystore cacerts.p12 | grep atoz
keytool -importkeystore -deststorepass changeit -destkeystore cacerts.p12 -srckeystore atoz.p12 -srcstoretype PKCS12
keytool -list -v -keystore cacerts.p12 | grep atoz
```
