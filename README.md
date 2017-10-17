# JMusicBot-gplaymusic
A GooglePlayMusic provider for the JMusicBot (https://github.com/BjoernPetersen/JMusicBot)

#### Requirements
In order to be able to use this plugin, you will need an active AllAccess subscription to Google's 
music streaming service [GooglePlayMusic](play.google.com/music/listen).

#### Download
You can find the latest build of this plugin
[here](https://FelixGail.github.io/CircleCIArtifactProvider/index.html?vcs-type=github&user=FelixGail&project=JMusicBot-gplaymusic&build=latest&token=052163ee37b6ca7653f730659f5980b8ad271138&branch=master&filter=successful&path=root/app/target/musicbot-gplaymusic.jar).

#### Installation
1. Install a version of the JMusicBot as well as an Mp3PlaybackFactory
(e.g. [JMusicBot-javafxPlayback](https://github.com/BjoernPetersen/JMusicBot-javafxPlayback))
2. Copy this [project](https://FelixGail.github.io/CircleCIArtifactProvider/index.html?vcs-type=github&user=FelixGail&project=JMusicBot-gplaymusic&build=latest&token=052163ee37b6ca7653f730659f5980b8ad271138&branch=master&filter=successful&path=root/app/target/musicbot-gplaymusic.jar)
into the plugins folder.
3. Start the MusicBot and configure this plugin:
    1. **Username:** Your Google username or email.
    2. **Password:** Your Google password or, if you are using 2-factor-authentication,
    an App-Password created [here](https://support.google.com/accounts/answer/185833).
    3. **AndroidID:** The IMEI of an android phone, that recently had GooglePlayMusic installed.<br>
    _In Android 7 it can be found in Settings -> About Phone -> Status -> IMEI information. This should be similiar for other versions_
    4. **Song Directory:** The Plugin will temporarily save downloaded songs in this directory.
    On closure this directory will be deleted.
