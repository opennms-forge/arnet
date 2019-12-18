# Deploying to Karaf
## Install
`mvn install`

## Deploy
`bundle:install -s wrap:mvn:org.java-websocket/Java-WebSocket/1.4.0`
`bundle:install -s wrap:mvn:org.opennms.arnet/model/1.0-SNAPSHOT`
`bundle:install -s mvn:org.jetbrains.kotlin/kotlin-osgi-bundle/1.3.61`
`bundle:install -s mvn:com.fasterxml.jackson.module/jackson-module-kotlin/2.9.8`
`bundle:install -s mvn:org.opennms.oia.streaming/ws-streamer/1.0-SNAPSHOT`