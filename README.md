<b>Telegram Messaging App first Trivia Bot!
This is customized for personal use, but can be easily edited to fit any Telegram channel or Telegram private messaging.</b>

In general, Application.java is where most of the customization is needed.

onIncomingMessageChat - is where you can customize the incoming messages in the chat.

processCommand -  is where you can add custom actions that you write in the chat and can work accordingly. typing “start” to start, “stop” to stop, “help” for help, etc.

In the workloop function - sendMessageChat has a number that it sends messages to, it states 1256792 at the moment, but you need to find out the group chat id where you want to add the trivia, and change to that accordingly.

loadTrivia - you can add custom names to users participating in the trivia with: idsToNames.put(). You need to find out the user's id in telegram and add that.

<b>Been getting a lot of emails and sorry I can't answer all of you, if you have any issues please submit an issue on github, that way I can help you better :)</b>

<b>Thanks, and happy triviaing!</b>
