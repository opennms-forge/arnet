# ARNet

ARNet is an 3D network visualization tool that integrates with OpenNMS.

## Prerequisites
1. Android Studio
2. An ARCore [supported device](//developers.google.com/ar/discover/supported-devices)

## Project Structure

ARNet is composed of three projects:
1. Model
2. Android Application
3. Backend

Note: the model must be built first prior to the others.

## Building / Running

1. Model
```
  cd model/
  ./gradlew publishToMavenLocal
```

2. Backend
```
  cd kafka2ws
  ./gradlew run
```

3. App

Use Android Studio to build and launch

Note: update constant 'WEB_SOCKET_SERVER' in class 'WebSocketConsumerService' to reflect the
IP and port of the backend.



