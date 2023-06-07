# Halcyon

 https://arxiv.org/abs/2304.10612

## Building Server Jar
```sh
mvn -Pserver clean package
```

## Building installers

Each command must be run on the appropriate platform.  Artifacts are located in dist folder.
```sh
mvn -Pserver clean package jpackage:jpackage@win
mvn -Pserver clean package jpackage:jpackage@mac
mvn -Pserver clean package jpackage:jpackage@ubuntu
mvn -Pserver clean package jpackage:jpackage@rhel
```

## Building Ingest program binary for feature files

This will require a functioning GraalVM native image environment - https://www.graalvm.org/22.3/reference-manual/native-image/)
```sh
mvn -Pingest clean package 
```

## Building Ingest Jar Version for feature files

This will require a functioning GraalVM native image environment - https://www.graalvm.org/22.3/reference-manual/native-image/)
```sh
mvn -Pingestjar clean package 
```

## SSL
```sh
openssl req -new -newkey rsa:2048 -nodes -keyout beak.key -out beak.csr
openssl pkcs12 -export -in beak.crt -inkey beak.key -name Halcyon -out beak.p12
keytool -importkeystore -deststorepass changeit -destkeystore cacerts -srckeystore beak.p12 -srcstoretype PKCS12
```

**adjective:**<br>
&nbsp;&nbsp;&nbsp;&nbsp;denoting a period of time in the past that was idyllically happy and peaceful.<br>
&nbsp;&nbsp;&nbsp;&nbsp;"the halcyon days of the mid-1980s, when profits were soaring"<br>

&nbsp;&nbsp;&nbsp;&nbsp;**Similar** - serene, calm, pleasant, balmy, tranquil, peaceful, temperate, mild, quiet, gentle, placid, still, windless, stormless, happy, carefree, blissful, golden, joyful, joyous, contented, idyllic, palmy, flourishing, thriving, prosperous, successful<br>
&nbsp;&nbsp;&nbsp;&nbsp;**Opposite** - stormy, troubled<br>

**noun:**<br>
&nbsp;&nbsp;&nbsp;&nbsp;1. a tropical Asian and African kingfisher with brightly colored plumage.<br>
&nbsp;&nbsp;&nbsp;&nbsp;2. a mythical bird said by ancient writers to breed in a nest floating at sea at the winter solstice, charming the wind and waves into calm.<br>
&nbsp;&nbsp;&nbsp;&nbsp;3. The name of a software system for managing Pathology Whole Slide Images and derived features from AI pipelines.<br>