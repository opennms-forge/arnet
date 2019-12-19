# ARNet [![CircleCI](https://circleci.com/gh/opennms-forge/arnet.svg?style=svg)](https://circleci.com/gh/opennms-forge/arnet)

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

## Updating/adding targets

Add image to the `app/src/main/assets/targets` directory and run:
```
arcoreimg build-db --input_images_directory=app/src/main/assets/targets --output_db_path=app/src/main/assets/targets.imgdb
```

See [arcoreimg](//developers.google.com/ar/develop/c/augmented-images/arcoreimg) for details

