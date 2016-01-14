package org.telegram.bot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.telegram.api.TLAbsUpdates;
import org.telegram.api.TLConfig;
import org.telegram.api.TLInputPeerChat;
import org.telegram.api.TLInputPeerContact;
import org.telegram.api.TLUpdateShortChatMessage;
import org.telegram.api.TLUpdateShortMessage;
import org.telegram.api.auth.TLAuthorization;
import org.telegram.api.auth.TLSentCode;
import org.telegram.api.engine.ApiCallback;
import org.telegram.api.engine.AppInfo;
import org.telegram.api.engine.LoggerInterface;
import org.telegram.api.engine.RpcCallbackEx;
import org.telegram.api.engine.RpcException;
import org.telegram.api.engine.TelegramApi;
import org.telegram.api.messages.TLAbsSentMessage;
import org.telegram.api.requests.TLRequestAccountUpdateStatus;
import org.telegram.api.requests.TLRequestAuthSendCode;
import org.telegram.api.requests.TLRequestAuthSignIn;
import org.telegram.api.requests.TLRequestHelpGetConfig;
import org.telegram.api.requests.TLRequestMessagesSendMessage;
import org.telegram.api.requests.TLRequestUpdatesGetState;
import org.telegram.api.updates.TLState;
import org.telegram.mtproto.log.LogInterface;
import org.telegram.mtproto.log.Logger;

import engine.MemoryApiState;

/**
 * Created by ex3ndr on 13.01.14.
 */
public class Application {
	private static HashMap<Integer, PeerState> userStates = new HashMap<Integer, PeerState>();
	private static HashMap<Integer, PeerState> chatStates = new HashMap<Integer, PeerState>();
	private static MemoryApiState apiState;
	private static TelegramApi api;
	private static Random rnd = new Random();
	private static long lastOnline = System.currentTimeMillis();
	private static ArrayList<Question> questions;
	private static boolean gameInProgress = false;
	private static HashMap<String, Integer> scores = new HashMap<String, Integer>();
	private static Random rand = new Random(System.currentTimeMillis());
	private static String expectedAnswer = "";
	private static final int warningStep = 15;
	private static final int questionTimeout = 60;
	private static long questionAskedAt;
	private static int allowedConsecutiveTimeouts;
	private static long currentConsecutiveTimeouts = 0;
	private static Timer timer = new Timer();
	private static HashMap<Integer, String> idsToNames = new HashMap<Integer, String>();
	
	public static String apiID = "";
	public static int chatRoomID = -1;
	
	public static void main(String[] args) throws IOException {
		disableLogging();
		createApi();
		loadTrivia();
		login();
		workLoop();
	}

	private static void loadTrivia() {
		questions = new ArrayList<Question>();
		try {
			for (int i = 0; i < 70; i++) {
				String filename = "questions_" + (i < 10 ? ("0" + i) : i);
				Scanner sc = new Scanner(new File(
						"src/org/telegram/bot/questions/" + filename));
				while (sc.hasNext()) {
					String[] line = sc.nextLine().split("`");
					if (line.length != 2) {
						continue;
					}
					questions.add(new Question(line[0], line[1]));
				}
				sc.close();
			}
		} catch (FileNotFoundException ex) {
			System.out.println("questions file not found");
		}
		
		//Here you can give personal names to the users participating
		idsToNames.put(12345, "ExamplePlayer1");

		File file = new File("scoresFile");
		FileInputStream f;
		try {
			if (!file.isFile()) {
				file.createNewFile();
			} else {
				f = new FileInputStream(file);
				if (f.available() > 0) {
					ObjectInputStream s = new ObjectInputStream(f);
					scores = (HashMap<String, Integer>) s.readObject();
					s.close();
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static synchronized String generateRandomString(int size) {
		String res = "";
		for (int i = 0; i < size; i++) {
			res += (char) ('a' + rnd.nextInt('z' - 'a'));
		}
		return res;
	}

	private static synchronized PeerState[] getAllSpamPeers() {
		ArrayList<PeerState> peerStates = new ArrayList<PeerState>();
		for (PeerState state : userStates.values()) {
			if (state.isSpamEnabled()) {
				peerStates.add(state);
			}
		}
		for (PeerState state : chatStates.values()) {
			if (state.isSpamEnabled()) {
				peerStates.add(state);
			}
		}
		return peerStates.toArray(new PeerState[0]);
	}

	private static synchronized PeerState getUserPeer(int uid) {
		if (!userStates.containsKey(uid)) {
			userStates.put(uid, new PeerState(uid, true));
		}

		return userStates.get(uid);
	}

	private static synchronized PeerState getChatPeer(int chatId) {
		if (!chatStates.containsKey(chatId)) {
			chatStates.put(chatId, new PeerState(chatId, false));
		}

		return chatStates.get(chatId);
	}

	private static void sendMessage(PeerState peerState, String message) {
		if (peerState.isUser()) {
			sendMessageUser(peerState.getId(), message);
		} else {
			sendMessageChat(peerState.getId(), message);
		}
	}

	private static void sendMessageChat(int chatId, String message) {
		api.doRpcCall(new TLRequestMessagesSendMessage(new TLInputPeerChat(
				chatId), message, rnd.nextInt()), 15 * 60000,
				new RpcCallbackEx<TLAbsSentMessage>() {
					@Override
					public void onConfirmed() {

					}

					@Override
					public void onResult(TLAbsSentMessage result) {

					}

					@Override
					public void onError(int errorCode, String message) {
					}
				});
	}

	private static void sendMessageUser(int uid, String message) {
		api.doRpcCall(new TLRequestMessagesSendMessage(new TLInputPeerContact(
				uid), message, rnd.nextInt()), 15 * 60000,
				new RpcCallbackEx<TLAbsSentMessage>() {
					@Override
					public void onConfirmed() {

					}

					@Override
					public void onResult(TLAbsSentMessage result) {

					}

					@Override
					public void onError(int errorCode, String message) {

					}
				});
	}

	private static void onIncomingMessageUser(int uid, String message) {
		// System.out.println("Incoming message from user #" + uid + ": "
		// + message);
		// PeerState peerState = getUserPeer(uid);
		// if (message.startsWith("bot") || message.startsWith("Bot")) {
		// sendMessageUser(uid, "Received: " + message);
		// processCommand(message.trim().substring(3).trim(), peerState);
		// } else {
		// if (peerState.isForwardingEnabled()) {
		// sendMessageUser(uid, "FW: " + message);
		// }
		// }
	}

	private static void onIncomingMessageChat(int userId, int chatId,
			String message) {
		System.out.println("Incoming message from in chat #" + chatId
				+ ", user #" + userId + " : " + message);
		PeerState peerState = getChatPeer(chatId);
		if (message.startsWith("bot") || message.startsWith("Bot")) {
			processCommand(message.trim().substring(3).trim(),
					getChatPeer(chatId), userId);
		} else if (message.equals("yar") || message.equals("Yar")
				|| message.startsWith("yar ") || message.endsWith(" yar")
				|| message.startsWith("Yar ") || message.endsWith(" Yar")
				|| message.contains(" yar ") || message.contains(" Yar ")) {
			sendMessage(getChatPeer(chatId),
					"Do not say mr mr geniuosity's name in vein.");
		} else if (message.equals("tara") || message.equals("Tara")
				|| message.startsWith("tara ") || message.endsWith(" tara")
				|| message.startsWith("Tara ") || message.endsWith(" Tara")
				|| message.contains(" tara ") || message.contains(" tara ")) {
			sendMessage(getChatPeer(chatId), "Tara is queen of the universe.");
		} else if (gameInProgress) {
			processCommand(message, getChatPeer(chatId), userId);
		} else {
			if (peerState.isForwardingEnabled()) {
				sendMessageChat(chatId, "FW: " + message);
			}
		}
	}

	private static String getWalkerString(int len, int position) {
		int realPosition = position % len * 2;
		if (realPosition > len) {
			realPosition = len - (realPosition - len);
		}
		String res = "|";
		for (int i = 0; i < realPosition; i++) {
			res += ".";
		}
		res += "\uD83D\uDEB6";
		for (int i = realPosition + 1; i < len; i++) {
			res += ".";
		}
		return res + "|";
	}

	private static void processCommand(String message, PeerState peerState,
			int userID) {
		// String[] args = message.split(" ");
		// if (args.length == 0) {
		// sendMessage(peerState, "Unknown command");
		// }
		String command = message.trim().toLowerCase();

		if (!gameInProgress) {

			if (command.equals("stop")) {
				currentConsecutiveTimeouts = 0;
				sendMessage(peerState, "huh? Stop what?! start a game first !");
				return;
			}

			if (command.equals("skip")) {
				currentConsecutiveTimeouts = 0;
				sendMessage(peerState, "huh? Skip what?! start a game first !");
				return;
			}

			if (message.equals("start")) {
				currentConsecutiveTimeouts = 0;
				sendMessage(peerState, "Game started !");
				sendMessage(peerState, "--> Play fair, don't cheat ! <--");
				gameInProgress = true;
				// User[] users = getUsers(channel);
				// for (int i = 0; i < users.length; i++) {
				//
				// if (!users[i].getNick().equals(getNick())) {
				// scores.put(users[i].getNick(), 0);
				// }
				// }

				sendRandomQuestion(peerState);
				return;
			}

		} else {

			if (message.equals("start")) {
				currentConsecutiveTimeouts = 0;
				sendMessage(peerState, "A game is already in progress");
				return;
			}

			if (message.equals("stop")) {
				currentConsecutiveTimeouts = 0;
				sendMessage(peerState, "Game ended !");
				gameInProgress = false;
				timer.cancel();
				return;
			}
			if (message.equals("skip")) {
				currentConsecutiveTimeouts = 0;
				sendMessage(peerState, "Skipping this question");
				sendRandomQuestion(peerState);
				return;
			}

			if (message.equalsIgnoreCase(expectedAnswer)) {
				// Integer score = scores.get(sender);
				// if (score != null) {
				currentConsecutiveTimeouts = 0;
				expectedAnswer = null;

				if (scores.containsKey(String.valueOf(userID))) {
					scores.put(String.valueOf(userID),
							scores.get(String.valueOf(userID)) + 1);
				} else {
					scores.put(String.valueOf(userID), 1);
				}
				if (!idsToNames.containsKey(userID)) {
					idsToNames.put(userID, "new_noob");
				}

				File file = new File("scoresFile");
				FileOutputStream f;
				try {
					f = new FileOutputStream(file);
					ObjectOutputStream s = new ObjectOutputStream(f);
					s.writeObject(scores);
					s.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				sendMessage(peerState, idsToNames.get(userID)
						+ " Got it right, well done !");

				sendRandomQuestion(peerState);
				// }
			}

		}

		if (message.equals("help")) {
			currentConsecutiveTimeouts = 0;
			sendMessage(peerState, "~~~~ List of my trivial commands ~~~~");
			sendMessage(peerState,
					"bot help: shows this list of commands ... obviously");
			sendMessage(peerState,
					"bot start: starts a trivia game with current users in channel");
			sendMessage(peerState, "bot stop: stops the current game");
			sendMessage(peerState, "bot skip: skips the current question");
			sendMessage(peerState,
					"bot changename 'name': changes your name to 'name'");
			// sendMessage(peerState, Colors.BOLD
			// + "!stat: shows the scoreboard");
			// sendMessage(channel, Colors.BOLD + "!disconnect: kills me !");
			sendMessage(peerState, "~~~~ List of my trivial commands ~~~~");
			return;
		}
		// if (message.equals("!disconnect")) {
		// sendMessage(channel, Colors.BOLD
		// + "gtg .. see you around, bye !");
		// timer.cancel();
		// try {
		// Thread.sleep(1000);
		// } catch (InterruptedException ex) {
		// Logger.getLogger(MyTriviaBot.class.getName()).log(
		// Level.SEVERE, null, ex);
		// }
		// disconnect();
		// System.out.println("Disconnected");
		// System.out.println("Quitting .. bye !");
		// System.exit(0);
		// }
		if (message.equals("stats")) {

			currentConsecutiveTimeouts = 0;
			String msg = "~~~~ Scoreboard ~~~~\n";
			Set<Entry<String, Integer>> scoresSet = scores.entrySet();

			if (scores.isEmpty()) {
				sendMessage(peerState, "No scores ..");
			} else {
				for (Entry<String, Integer> score : scoresSet) {
					if (score.getValue() != 0) {
						msg += idsToNames.get(Integer.parseInt(score.getKey()))
								+ ": " + score.getValue() + "\n";
					}
				}
			}
			msg += "~~~~ Scoreboard ~~~~";
			sendMessage(peerState, msg);
			return;
		}

		if (message.startsWith("changename")) {

			String name = message.substring(11);
			sendMessage(peerState, idsToNames.get(userID) + " changed name to"
					+ name);
			idsToNames.put(userID, name);
			return;
		}
		// }

		// if (command.equalsIgnoreCase("enable_forward")) {
		// sendMessage(peerState, "Forwarding enabled");
		// peerState.setForwardingEnabled(true);
		// } else if (command.equalsIgnoreCase("disable_forward")) {
		// peerState.setForwardingEnabled(false);
		// sendMessage(peerState, "Forwarding disabled");
		// } else if (command.equalsIgnoreCase("random")) {
		// if (args.length == 2) {
		// int count = Integer.parseInt(args[1].trim());
		// if (count <= 0) {
		// count = 32;
		// }
		// if (count > 4 * 1024) {
		// sendMessage(peerState, WarAndPeace.ANGRY);
		// } else {
		// sendMessage(peerState, "Random: " + (generateRandomString(count)));
		// }
		// } else {
		// sendMessage(peerState, "Random: " + (generateRandomString(32)));
		// }
		// } else if (command.equalsIgnoreCase("start_flood")) {
		// int delay = 15;
		// if (args.length == 2) {
		// delay = Integer.parseInt(args[1].trim());
		// }
		// peerState.setMessageSendDelay(delay);
		// peerState.setSpamEnabled(true);
		// peerState.setLastMessageSentTime(0);
		// sendMessage(peerState, "Flood enabled with delay " + delay + " sec");
		// } else if (command.equals("stop_flood")) {
		// peerState.setSpamEnabled(false);
		// sendMessage(peerState, "Flood disabled");
		// } else if (command.equalsIgnoreCase("ping")) {
		// for (int i = 0; i < 1; i++) {
		// sendMessage(peerState, "pong " + getWalkerString(10, i) + " #" + i);
		// try {
		// Thread.sleep(20);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
		// }
		// } else if (command.equalsIgnoreCase("jew_ping")) {
		// for (int i = 0; i < 5; i++) {
		// sendMessage(peerState, WarAndPeace.ANGRY);
		// }
		// } else if (command.equalsIgnoreCase("jew")) {
		// sendMessage(peerState, WarAndPeace.TEXT);
		// } else if (command.equalsIgnoreCase("jew2")) {
		// sendMessage(peerState, WarAndPeace.TEXT2);
		// } else if (command.equalsIgnoreCase("help")) {
		// sendMessage(peerState, "Bot commands:\n" +
		// "bot enable_forward/disable_forward - forwarding of incoming messages\n"
		// +
		// "bot random [len] - Write random string of length [len] (default = 32)\n"
		// +
		// "bot ping - ping with pong\n" +
		// "bot bear - bear ascii\n" +
		// "bot jew_ping - ping with 5 angry jew fragments\n");
		// } else {
		// sendMessage(peerState, "Unknown command '" + args[0] + "'");
		// }
	}

//	private static String getName(Entry<String, Integer> score) {
//		String name = "";
//		if (Integer.parseInt(score.getKey()) == 11657727) {
//			name = "Mar_Tzipan";
//		} else if (Integer.parseInt(score.getKey()) == 9103759) {
//			name = "Bob";
//		} else if (Integer.parseInt(score.getKey()) == 8459543) {
//			name = "Merk";
//		} else if (Integer.parseInt(score.getKey()) == 9508544) {
//			name = "Snake";
//		} else {
//			name = "Yarr";
//		}
//		return name;
//	}

	private static void sendRandomQuestion(final PeerState peerState) {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Question question = questions.get(rand.nextInt(questions.size()));
		expectedAnswer = question.getAnswer();
		sendMessage(peerState, question.getQuestion());
		questionAskedAt = System.currentTimeMillis();
		timer.cancel();

		timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				sendMessage(peerState, "Timeout! The correct answer was: "
						+ expectedAnswer);
				currentConsecutiveTimeouts++;
				timer.cancel();
				sendRandomQuestion(peerState);
			}
		}, questionTimeout * 1000, questionTimeout * 1000);

		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				long timeLeft = questionTimeout
						- ((System.currentTimeMillis() - questionAskedAt))
						/ 1000;
				String clue = "";
				if (expectedAnswer.length() >= 1) {
					if (timeLeft == 45) {
						for (int i = 0; i < expectedAnswer.length(); i++) {
							if (expectedAnswer.charAt(i) == ' ') {
								clue += " | ";
							} else {
								clue += "_ ";
							}
						}
					} else if (timeLeft == 30) {
						int show = 1;
						for (int i = 0; i < expectedAnswer.length(); i++) {
							if (show == 1) {
								if (expectedAnswer.length() > 1) {
									clue += expectedAnswer.charAt(i);
								}
								show = 0;
							} else if (expectedAnswer.charAt(i) == ' ') {
								clue += " | ";
								show = 1;
							} else {
								clue += "_ ";
								show = 0;
							}
						}
					} else {
						for (int i = 0; i < expectedAnswer.length(); i++) {
							if (i % 2 == 0) {
								if (expectedAnswer.length() > 1) {
									clue += expectedAnswer.charAt(i);
								}
							} else if (expectedAnswer.charAt(i) == ' ') {
								clue += " | ";
							} else {
								clue += "_ ";
							}
						}
					}
				}
				sendMessage(peerState, timeLeft + " seconds remaining."
						+ " clue: " + clue);
			}
		}, warningStep * 1000, warningStep * 1000);
	}

	private static void workLoop() {
		sendMessageChat(chatRoomID, "Hello World !");
		sendMessageChat(chatRoomID,
				"My name is CrappyBot, and i am apparently a trivia bot !");
		sendMessageChat(chatRoomID,
				"Type 'bot help' for a list of available commands");
		sendMessageChat(chatRoomID, "There are " + questions.size()
				+ " questions on CrappyBot");

		while (true) {
			try {
				PeerState[] states = getAllSpamPeers();
				for (PeerState state : states) {
					if (state.isSpamEnabled()) {
						if (System.currentTimeMillis()
								- state.getLastMessageSentTime() > state
								.getMessageSendDelay() * 1000L) {
							int messageId = state.getMessagesSent() + 1;
							state.setMessagesSent(messageId);
							sendMessage(state,
									"Flood " + getWalkerString(10, messageId)
											+ " #" + messageId);
							state.setLastMessageSentTime(System
									.currentTimeMillis());
						}
					}
				}
				if (System.currentTimeMillis() - lastOnline > 60 * 1000) {
					api.doRpcCallWeak(new TLRequestAccountUpdateStatus(false));
					lastOnline = System.currentTimeMillis();
				}
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static void disableLogging() {
		Logger.registerInterface(new LogInterface() {
			@Override
			public void w(String tag, String message) {

			}

			@Override
			public void d(String tag, String message) {

			}

			@Override
			public void e(String tag, Throwable t) {

			}
		});
		org.telegram.api.engine.Logger.registerInterface(new LoggerInterface() {
			@Override
			public void w(String tag, String message) {

			}

			@Override
			public void d(String tag, String message) {

			}

			@Override
			public void e(String tag, Throwable t) {

			}
		});
	}

	private static void createApi() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));
		System.out.print("Use test DC? (write test for test servers): ");
		String res = reader.readLine();
		boolean useTest = res.equals("test");
		if (!useTest) {
			System.out.println("Using production servers");
		} else {
			System.out.println("Using test servers");
		}
		apiState = new MemoryApiState(useTest);
		api = new TelegramApi(apiState, new AppInfo(5, "console", "???", "???",
				"en"), new ApiCallback() {

			@Override
			public void onAuthCancelled(TelegramApi api) {

			}

			@Override
			public void onUpdatesInvalidated(TelegramApi api) {

			}

			@Override
			public void onUpdate(TLAbsUpdates updates) {
				if (updates instanceof TLUpdateShortMessage) {
					onIncomingMessageUser(
							((TLUpdateShortMessage) updates).getFromId(),
							((TLUpdateShortMessage) updates).getMessage());
				} else if (updates instanceof TLUpdateShortChatMessage) {
					onIncomingMessageChat(
							((TLUpdateShortChatMessage) updates).getFromId(),
							((TLUpdateShortChatMessage) updates).getChatId(),
							((TLUpdateShortChatMessage) updates).getMessage());
				}
			}
		});
	}

	private static void login() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));
		System.out.print("Loading fresh DC list...");
		TLConfig config = api.doRpcCallNonAuth(new TLRequestHelpGetConfig());
		apiState.updateSettings(config);
		System.out.println("completed.");
		System.out.print("Phone number for bot:");
		String phone = reader.readLine();
		System.out.print("Sending sms to phone " + phone + "...");
		TLSentCode sentCode;
		try {
			sentCode = api.doRpcCallNonAuth(new TLRequestAuthSendCode(phone, 0,
					5, apiID, "en"));
		} catch (RpcException e) {
			if (e.getErrorCode() == 303) {
				int destDC;
				if (e.getErrorTag().startsWith("NETWORK_MIGRATE_")) {
					destDC = Integer.parseInt(e.getErrorTag().substring(
							"NETWORK_MIGRATE_".length()));
				} else if (e.getErrorTag().startsWith("PHONE_MIGRATE_")) {
					destDC = Integer.parseInt(e.getErrorTag().substring(
							"PHONE_MIGRATE_".length()));
				} else if (e.getErrorTag().startsWith("USER_MIGRATE_")) {
					destDC = Integer.parseInt(e.getErrorTag().substring(
							"USER_MIGRATE_".length()));
				} else {
					throw e;
				}
				api.switchToDc(destDC);
				sentCode = api.doRpcCallNonAuth(new TLRequestAuthSendCode(
						phone, 0, 5, apiID, "en"));
			} else {
				throw e;
			}
		}
		System.out.println("sent.");
		System.out.print("Activation code:");
		String code = reader.readLine();
		TLAuthorization auth = api.doRpcCallNonAuth(new TLRequestAuthSignIn(
				phone, sentCode.getPhoneCodeHash(), code));
		apiState.setAuthenticated(apiState.getPrimaryDc(), true);
		System.out.println("Activation complete.");
		System.out.print("Loading initial state...");
		TLState state = api.doRpcCall(new TLRequestUpdatesGetState());
		System.out.println("loaded.");
	}
}
