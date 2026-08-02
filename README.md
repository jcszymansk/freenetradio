# FreeNetRadio


### What is this ? ###

* **FreeNetRadio** uses Android's media framework to stream worldwide radio stations on phones, tablets, and Android Auto.
* This project is use [Community Radio Browser's API](http://www.radio-browser.info) and [Web Radio](https://jcorporation.github.io/webradiodb) - services that provide a list of radio stations broadcasting their live stream on the Internet.
* Playlist parser is provided by [William Seemann](https://github.com/wseemann/JavaPlaylistParser)
* Playback powered by [Exo Player](https://github.com/google/ExoPlayer)
* Android requirements : Android 4.2 (API level 17) (new APIs for implementing audio playback that is compatible with Auto) or newer.

### Permissions used ###

* INTERNET - To access internet connection.
* ACCESS_NETWORK_STATE - To monitor Internet connection state, detect connect and reconnect states.
* WAKE_LOCK - To keep screen on while playing Radio Station.
* ACCESS_COARSE_LOCATION - On user's demand only - to select Country for user based on Location. This helps to navigate local Radio Stations.
* READ_EXTERNAL_STORAGE (Android 12 and older), READ_MEDIA_IMAGES (Android 13 and newer, on user's demand only) to read image from phone's memory when set it as image for Local Radio Station.
* FOREGROUND_SERVICE - To keep service active while playing stream.
* BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT - On user's demand only - to handle connection with a Bluetooth device.

### Project status ###

* This revived source currently supports phone/tablet Android and Android Auto.
* Android TV and native Android Automotive OS are not maintained.
* No public binary release is currently provided by this repository.
