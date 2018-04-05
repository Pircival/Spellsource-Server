package com.hiddenswitch.spellsource;

import ch.qos.logback.classic.Level;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;
import com.hiddenswitch.spellsource.client.ApiClient;
import com.hiddenswitch.spellsource.client.ApiException;
import com.hiddenswitch.spellsource.client.api.DefaultApi;
import com.hiddenswitch.spellsource.client.models.*;
import com.hiddenswitch.spellsource.impl.*;
import com.hiddenswitch.spellsource.models.CreateGameSessionRequest;
import com.hiddenswitch.spellsource.common.DeckCreateRequest;
import com.hiddenswitch.spellsource.models.DeckCreateResponse;
import com.hiddenswitch.spellsource.models.GetCardResponse;
import com.hiddenswitch.spellsource.util.*;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.SendContext;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Repeat;
import net.demilich.metastone.game.behaviour.PlayRandomBehaviour;
import net.demilich.metastone.game.cards.CardCatalogue;
import net.demilich.metastone.game.decks.DeckFormat;
import net.demilich.metastone.game.entities.heroes.HeroClass;
import net.demilich.metastone.game.events.GameEventType;
import net.demilich.metastone.game.targeting.EntityReference;
import net.demilich.metastone.game.utils.Attribute;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.ext.sync.Sync.awaitResult;

/**
 * Created by bberman on 2/18/17.
 */
public class GatewayTest extends ServiceTest<GatewayImpl> {
	private String deploymentId;
	private LogicImpl logic;
	private ClusteredGamesImpl games;
	private BotsImpl bots;
	private static DefaultApi defaultApi = new DefaultApi();
	private MatchmakingImpl matchmaking;
	private static int currentUser = 0;


	static {
		defaultApi.getApiClient().setBasePath(UnityClient.basePath);
	}


	@Test(timeout = 100000L)
	public void testAccountFlow(TestContext context) throws InterruptedException {
		wrap(context);
		Set<String> decks = Spellsource.spellsource().getStandardDecks().stream().map(DeckCreateRequest::getName).collect(Collectors.toSet());

		final int expectedCount = 10;
		CountDownLatch latch = new CountDownLatch(expectedCount);
		CompositeFuture.join(Collections.nCopies(2, Arrays.asList(
				new GatewayImpl(),
				new CardsImpl(),
				new DecksImpl(),
				new LogicImpl(),
				new AccountsImpl(),
				new InventoryImpl()))
				.stream().flatMap(Collection::stream).map(v -> {
					Future<String> future = Future.future();
					vertx.deployVerticle(v, future);
					return future;
				}).collect(Collectors.toList())).setHandler(then -> {
			// Interleave these calls
			for (int i = 0; i < expectedCount; i++) {
				final int j = i;

				Thread t = new Thread(() -> {
					ApiClient client = new ApiClient().setBasePath(UnityClient.basePath);
//					client.getHttpClient().setConnectTimeout(2, TimeUnit.MINUTES);
//					client.getHttpClient().setWriteTimeout(2, TimeUnit.MINUTES);
//					client.getHttpClient().setReadTimeout(2, TimeUnit.MINUTES);
					DefaultApi api = new DefaultApi(client);
					String random = RandomStringUtils.randomAlphanumeric(36) + Integer.toString(j);
					try {
						CreateAccountResponse response1 = api.createAccount(new CreateAccountRequest()
								.name("username" + random)
								.email("email" + random + "@email.com")
								.password("password"));

						api.getApiClient().setApiKey(response1.getLoginToken());
						final String userId = response1.getAccount().getId();
						getContext().assertNotNull(userId);
						GetAccountsResponse response2 = api.getAccount(userId);
						getContext().assertTrue(response2.getAccounts().size() > 0);

						for (Account account : new Account[]{response1.getAccount(), response2.getAccounts().get(0)}) {
							getContext().assertNotNull(account.getId());
							getContext().assertNotNull(account.getEmail());
							getContext().assertNotNull(account.getName());
							getContext().assertNotNull(account.getPersonalCollection());
							getContext().assertNotNull(account.getDecks());
							getContext().assertTrue(account.getDecks().size() == Spellsource.spellsource().getStandardDecks().size());
							getContext().assertTrue(account.getDecks().stream().map(InventoryCollection::getName).collect(Collectors.toSet()).containsAll(decks));
							getContext().assertTrue(account.getPersonalCollection().getInventory().size() > 0);
						}
					} catch (ApiException e) {
						getContext().fail("API error: " + e.getMessage());
						return;
					}
					latch.countDown();
				});

				t.start();
			}
		});

		latch.await(90L, TimeUnit.SECONDS);
		getContext().assertEquals(latch.getCount(), 0L);
	}

	@Test
	public void testLoginWithInvalidToken(TestContext context) throws ApiException {
		DefaultApi api = new DefaultApi(new ApiClient().setBasePath(UnityClient.basePath));
		CreateAccountResponse car = api.createAccount(new CreateAccountRequest()
				.email("test@test.com")
				.name("name")
				.password("password"));
		api.getApiClient().setApiKey(car.getLoginToken());
		GetAccountsResponse accountsResponse = api.getAccount(car.getAccount().getId());
		// Assert we have access to private information here
		context.assertNotNull(accountsResponse.getAccounts().get(0).getEmail());
		// Change the key to something differently invalid
		api.getApiClient().setApiKey("invaliduserid:invalidtoken");
		try {
			accountsResponse = api.getAccount(car.getAccount().getId());
			context.fail("Successfully received account when we shouldn't have had.");
		} catch (ApiException ex) {
			context.assertEquals(ex.getCode(), 403, "Assert not authorized");
		}
		// Change the key to something invalid
		api.getApiClient().setApiKey(car.getAccount().getId() + ":invalidtoken");
		try {
			accountsResponse = api.getAccount(car.getAccount().getId());
			context.fail("Successfully received account when we shouldn't have had.");
		} catch (ApiException ex) {
			context.assertEquals(ex.getCode(), 403, "Assert not authorized");
		}
	}

	@Test
	public void testUnityClient(TestContext context) throws InterruptedException, SuspendExecution {
		Logging.setLoggingLevel(Level.ERROR);
		wrap(context);
		final Async async = context.async();

		for (int i = 0; i < 10; i++) {
			UnityClient client = new UnityClient(getContext());
			client.createUserAccount(null);
			client.matchmakeAndPlayAgainstAI(null);
			client.waitUntilDone();
			getContext().assertTrue(client.isGameOver());
		}
		async.complete();
		unwrap();
	}

	@Test(timeout = 800000L)
	public void testSimultaneousGames(TestContext context) throws InterruptedException, SuspendExecution {
		Logging.setLoggingLevel(Level.DEBUG);
		wrap(context);
		final int processorCount = Runtime.getRuntime().availableProcessors();
		final int count = processorCount * 3;
		CountDownLatch latch = new CountDownLatch(count);
		CompositeFuture.join(Collections.nCopies(2, Arrays.asList(
				new GatewayImpl(),
				new CardsImpl(),
				new DecksImpl(),
				new LogicImpl(),
				new AccountsImpl(),
				new ClusteredGamesImpl(),
				new InventoryImpl()))
				.stream().flatMap(Collection::stream).map(v -> {
					Future<String> future = Future.future();
					vertx.deployVerticle(v, future);
					return future;
				}).collect(Collectors.toList())).setHandler(then -> {
			Stream.generate(() -> new Thread(() -> {
				UnityClient client = new UnityClient(getContext());
				client.createUserAccount(null);
				client.matchmakeAndPlay(null);
				client.waitUntilDone();
				getContext().assertTrue(client.isGameOver());
				latch.countDown();
			})).limit(count).forEach(Thread::start);
		});

		// Random games can take quite a long time to finish so be patient...
		latch.await(320L, TimeUnit.SECONDS);
		getContext().assertEquals(latch.getCount(), 0L);
		unwrap();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testScenario(TestContext context) throws InterruptedException, SuspendExecution {
		Logging.setLoggingLevel(Level.ERROR);
		wrap(context);
		final Async async = context.async();

		UnityClient client = new UnityClient(getContext()) {
			@Override
			protected void assertValidStateAndChanges(ServerToClientMessage message) {
				super.assertValidStateAndChanges(message);
				getContext().assertTrue(message.getGameState().getEntities().stream().filter(e -> e.getCardId() != null && e.getCardId().equals("hero_necromancer")).count() >= 1L);
			}
		};

		client.createUserAccount(null);
		client.matchmakeAndPlayAgainstAI(client.getAccount().getDecks()
				.stream().filter(d -> d.getName().equals("Necromancer (Tavern Brawl)")).findFirst().orElseThrow(AssertionError::new).getId());
		client.waitUntilDone();
		getContext().assertTrue(client.isGameOver());

		async.complete();
		unwrap();
	}

	@Test(timeout = 25000L)
	public void testDisconnectingUnityClient(TestContext context) {
		wrap(context);
		Logging.setLoggingLevel(Level.ERROR);
		getContext().assertEquals(Games.getDefaultNoActivityTimeout(), 8000L);

		UnityClient client = new UnityClient(getContext(), 1);
		client.createUserAccount(null);
		final String token = client.getToken();
		final String userId = client.getAccount().getId();
		client.matchmakeAndPlayAgainstAI(null);

		// Assert that session was closed
		wrapSync(context, () -> {
			// wait 10 seconds
			Strand.sleep(10000L);
			getContext().assertEquals(null, Matchmaking.getQueue(vertx).get(new UserId(userId)));

			Boolean done = awaitResult(h -> vertx.executeBlocking((then) -> {
				UnityClient client2 = new UnityClient(context, token);
				try {
					boolean isInMatch = client2.getApi().getAccount(userId).getAccounts().get(0).isInMatch();
					then.complete(isInMatch);
				} catch (ApiException e) {
					then.fail(new AssertionError());
				}
			}, h));

			getContext().assertEquals(false, done);
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testDistinctDecks(TestContext context) throws InterruptedException, SuspendExecution {
		Logging.setLoggingLevel(Level.ERROR);
		wrap(context);

		Handler<SendContext> interceptor = interceptGameCreate(request -> {
			getContext().assertNotEquals(request.getPregame1().getDeck().getName(), request.getPregame2().getDeck().getName(), "The decks are distinct between the two users.");
		});

		UnityClient client1 = new UnityClient(getContext());
		Thread clientThread1 = new Thread(() -> {
			client1.createUserAccount("user1");
			final String startDeckId1 = client1.getAccount().getDecks().stream().filter(p -> p.getName().equals(Spellsource.spellsource().getStandardDecks().get(0).getName())).findFirst().get().getId();
			client1.matchmakeAndPlay(startDeckId1);
		});
		UnityClient client2 = new UnityClient(getContext());
		Thread clientThread2 = new Thread(() -> {
			client2.createUserAccount("user2");
			String startDeckId2 = client2.getAccount().getDecks().stream().filter(p -> p.getName().equals(Spellsource.spellsource().getStandardDecks().get(1).getName())).findFirst().get().getId();
			client2.matchmakeAndPlay(startDeckId2);
		});
		clientThread1.start();
		clientThread2.start();
		float time = 0f;
		while ((!client1.isGameOver() || !client2.isGameOver()) && time < 60f) {
			Strand.sleep(1000);
			time += 1f;
		}
		getContext().assertTrue(client1.isGameOver(), "The client ended the game");
		getContext().assertTrue(client2.isGameOver(), "The client ended the game");
		vertx.eventBus().removeInterceptor(interceptor);
		unwrap();
	}

	private Handler<SendContext> interceptGameCreate(Consumer<CreateGameSessionRequest> assertInHere) {
		final Handler<SendContext> interceptor = h -> {
			if (h.message().address().equals(Rpc.getAddress(Games.class, games -> games.createGameSession(null)))) {
				Message<Buffer> message = h.message();
				VertxBufferInputStream inputStream = new VertxBufferInputStream(message.body());

				CreateGameSessionRequest request = null;
				try {
					request = Serialization.deserialize(inputStream);
				} catch (IOException | ClassNotFoundException e) {
					getContext().fail(e.getMessage());
				}

				if (request != null) {
					assertInHere.accept(request);
				} else {
					getContext().fail("Request was null.");
				}

			}
			h.next();
		};
		vertx.eventBus().addInterceptor(interceptor);
		return interceptor;
	}

	@Test
	public void testMinionatePersistenceApi(TestContext context) {
		Logging.setLoggingLevel(Level.ERROR);
		wrap(context);
		ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<Long>();

		// Use a random attribute to test for persistence
		vertx.runOnContext(ignored -> {
			Spellsource.spellsource().persistAttribute("reserved-attribute-1", GameEventType.TURN_END, Attribute.RESERVED_INTEGER_4, persistenceContext -> {
				// Save the turn number to this yogg attribute
				long updated = persistenceContext.update(EntityReference.ALL_MINIONS, persistenceContext.event().getGameContext().getTurn());
				queue.add(updated);
			});
		});


		// Start a game and assert that there are entities with all random yogg
		vertx.executeBlocking(done -> {
			UnityClient client = new UnityClient(context);
			client.createUserAccount();
			// The user needs a deck of persistent effect cards
			final DecksPutResponse decksPutResponse;
			try {

				decksPutResponse = client.getApi().decksPut(new DecksPutRequest()
						.deckList("### Persistence Test Deck\n" +
								"Hero Class: Violet\n" +
								"30x Persistence Test Minion"));
			} catch (ApiException e) {
				getContext().fail(e);
				return;
			}
			client.matchmakeAndPlayAgainstAI(decksPutResponse.getDeckId());
			client.waitUntilDone();
			getContext().assertTrue(client.isGameOver());
			done.complete();
		}, context.asyncAssertSuccess(also -> {
			context.assertTrue(queue.stream().anyMatch(l -> l > 0L), "Any number of the entities updated was greater than zero.");
			Mongo.mongo().client().count(Inventory.INVENTORY,
					QuickJson.json("facts." + Attribute.RESERVED_INTEGER_4.toKeyCase(), QuickJson.json("$exists", true)),
					context.asyncAssertSuccess(count -> {
						context.assertTrue(count > 0L, "There is at least one inventory item that has the attribute that we configured to listen for.");
					}));
		}));
	}

	@Test
	public void testWeaponActionReceived(TestContext context) {
		Logging.setLoggingLevel(Level.ERROR);
		wrap(context);
		Async async = context.async();
		vertx.runOnContext(ignored -> {
			AtomicBoolean didGetPlayWeaponAction = new AtomicBoolean(false);
			UnityClient client = new UnityClient(context) {
				@Override
				protected void assertValidActions(ServerToClientMessage message) {
					super.assertValidActions(message);

					if (message.getMessageType() == MessageType.ON_REQUEST_ACTION
							&& message.getActions().getWeapons() != null
							&& message.getActions().getWeapons().size() > 0) {
						didGetPlayWeaponAction.set(true);
					}
				}
			};
			final String[] deckId = new String[1];
			vertx.executeBlocking(done -> {
				client.createUserAccount(null);
				Fiber<Void> fiber = new Fiber<Void>(io.vertx.ext.sync.Sync.getContextScheduler(), () -> {
					DeckCreateResponse res = service.getDecks().createDeck(new DeckCreateRequest()
							.withUserId(client.getAccount().getId())
							.withHeroClass(HeroClass.BLACK)
							.withName("Test Weapon Deck")
							.withCardIds(Collections.nCopies(30, "weapon_clandestine_laser")));
					deckId[0] = res.getDeckId();
				}).start();
				while (deckId[0] == null) {
					try {
						Strand.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (SuspendExecution suspendExecution) {
						suspendExecution.printStackTrace();
					}
				}
				done.handle(Future.succeededFuture());
			}, true, context.asyncAssertSuccess(then -> {
				vertx.executeBlocking(done2 -> {
					client.matchmakeAndPlayAgainstAI(deckId[0]);
					client.waitUntilDone();
					getContext().assertTrue(client.isGameOver());
					getContext().assertTrue(didGetPlayWeaponAction.get());
					done2.handle(Future.succeededFuture());
				}, true, context.asyncAssertSuccess(finallyy -> {
					async.complete();
				}));
			}));
		});
	}

	@Test
	public void testCardsCollection(TestContext context) throws ApiException {
		DefaultApi defaultApi = new DefaultApi();
		defaultApi.getApiClient().setBasePath(UnityClient.basePath);

		GetCardsResponse response1 = defaultApi.getCards(null);
		final long count = CardCatalogue.getRecords().values().stream().filter(c -> c.getDesc().collectible
				&& DeckFormat.CUSTOM.isInFormat(c.getDesc().set)).count();
		context.assertEquals((long) response1.getCards().size(), count);
		try {
			GetCardsResponse response2 = defaultApi.getCards(response1.getVersion());
			context.fail();
		} catch (ApiException ex) {
			context.assertEquals(ex.getCode(), 304);
		}

		GetCardsResponse response3 = defaultApi.getCards("invalid token");
		context.assertNotNull(response3);
		context.assertEquals(response3.getVersion(), response1.getVersion());
	}

	public CreateAccountResponse createRandomAccount(TestContext testContext, DefaultApi defaultApi) {
		String username = "tomer" + currentUser;
		String email = "tomer" + (currentUser++) + "@gmail.com";

		CreateAccountResponse createAccountResponse = null;
		try {
			createAccountResponse = defaultApi.createAccount(
					new CreateAccountRequest().email(email).name(username).password("1357913579"));
		} catch (ApiException e) {
			testContext.fail("failed creating random account " + username + " with error: " + e.getMessage());
		}
		testContext.assertNotNull(createAccountResponse, "first account is null");
		return createAccountResponse;
	}

	@Test
	public void testFriendsApi(TestContext testContext) {
		// create first account
		CreateAccountResponse createAccount1Response = createRandomAccount(testContext, defaultApi);

		// authenticate with first account
		String token = createAccount1Response.getLoginToken();
		testContext.assertNotNull(token, "auth token is null");
		defaultApi.getApiClient().setApiKey(token);

		// test putting friend that does not exist
		FriendPutResponse friendPutResponseDoesNotExist = null;
		try {
			friendPutResponseDoesNotExist = defaultApi.friendPut(new FriendPutRequest().friendId("idontexist"));
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(), "Friend doesn't exist. Should return 404");
		}
		testContext.assertNull(friendPutResponseDoesNotExist);

		// create second account
		CreateAccountResponse createAccount2Response = createRandomAccount(testContext, defaultApi);

		// add second account as friend
		FriendPutResponse friendPutResponse = null;
		try {
			friendPutResponse = defaultApi.friendPut(
					new FriendPutRequest().friendId(createAccount2Response.getAccount().getId()));
		} catch (ApiException e) {
			testContext.assertEquals(200, e.getCode(), "Adding new friend. Should return 200");
		}
		testContext.assertEquals(
				friendPutResponse.getFriend().getFriendid(), createAccount2Response.getAccount().getId());

		// test putting friend that already exists
		try {
			defaultApi.friendPut(new FriendPutRequest().friendId(createAccount2Response.getAccount().getId()));
		} catch (ApiException e) {
			testContext.assertEquals(409, e.getCode(), "Adding existing friend. Should return 409");
		}

		// test putting friend that already exists - second direction
		defaultApi.getApiClient().setApiKey(createAccount2Response.getLoginToken()); //reauth as friend
		try {
			defaultApi.friendPut(new FriendPutRequest().friendId(createAccount1Response.getAccount().getId()));
		} catch (ApiException e) {
			testContext.assertEquals(409, e.getCode(),
					"Adding existing friend (second direction). Should return 409");
		}

		// unfriend a user that doesn't exist
		try {
			defaultApi.friendDelete("idontexist");
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(),
					"Friend account doesn't exist. Should return 404");
		}

		// unfriend the first user
		UnfriendResponse unfriendResponse = null;
		try {
			unfriendResponse = defaultApi.friendDelete(createAccount1Response.getAccount().getId());
		} catch (ApiException e) {
			testContext.assertEquals(200, e.getCode(),
					"Unfriending an existing friend. expecting 200");
		}
		testContext.assertNotNull(unfriendResponse.getDeletedFriend(),
				"unfriend response should include the friend details");
		testContext.assertEquals(unfriendResponse.getDeletedFriend().getFriendid(),
				createAccount1Response.getAccount().getId());

		// try to unfriend the first user again
		try {
			defaultApi.friendDelete(createAccount1Response.getAccount().getId());
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(), "Not friends. should return 404");
		}

		// try to unfriend from the other direction
		defaultApi.getApiClient().setApiKey(createAccount1Response.getLoginToken());
		try {
			defaultApi.friendDelete(createAccount2Response.getAccount().getId());
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(),
					"Not friends (2nd direction). should return 404");
		}
	}


	@Test
	public void testConversation(TestContext testContext) {

		String MSG1 = "TEST1";
		String MSG2 = "TEST2";

		//bootstrap three accounts
		DefaultApi defaultApi = new DefaultApi();
		defaultApi.getApiClient().setBasePath(UnityClient.basePath); //TODO: read from configuration
		CreateAccountResponse createAccount1Response = createRandomAccount(testContext, defaultApi);
		defaultApi.getApiClient().setApiKey(createAccount1Response.getLoginToken());
		CreateAccountResponse createAccount2Response = createRandomAccount(testContext, defaultApi);
		CreateAccountResponse createAccount3Response = createRandomAccount(testContext, defaultApi);

		//simple message
		SendMessageRequest msg1Request = new SendMessageRequest().text(MSG1);
		SendMessageRequest msg2Request = new SendMessageRequest().text(MSG2);

		//send a message to a friend that doesn't exist
		try {
			defaultApi.sendFriendMessage("notafriend", msg1Request);
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(), "User shouldn't exist. expecting 404");
		}

		//send a message to a user that's not a friend
		try {
			defaultApi.sendFriendMessage(createAccount2Response.getAccount().getId(), msg1Request);
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(), "Send friend a message. User not a friend expecting 404");
		}

		//get conversation with a friend that doesn't exist
		try {
			defaultApi.getFriendConversation(createAccount2Response.getAccount().getId());
		} catch (ApiException e) {
			testContext.assertEquals(404, e.getCode(), "Get User conversation. not a friend expecting 404");
		}

		//add friend
		try {
			defaultApi.friendPut(new FriendPutRequest().friendId(createAccount2Response.getAccount().getId()));
		} catch (ApiException e) {
			testContext.assertTrue(false, "Adding new friend. Got : " + e.getMessage());

		}

		//send message 1
		SendMessageResponse sendMsg1Response = null;
		try {
			sendMsg1Response = defaultApi.sendFriendMessage(createAccount2Response.getAccount().getId(), msg1Request);
		} catch (ApiException e) {
			testContext.assertTrue(false, "Sending message (1) to a friend. Got : " + e.getMessage());
		}
		testContext.assertNotNull(sendMsg1Response);
		testContext.assertEquals(createAccount1Response.getAccount().getId(), sendMsg1Response.getMessage().getAuthorId());
		testContext.assertEquals(createAccount1Response.getAccount().getName(), sendMsg1Response.getMessage().getAuthorDisplayName());
		testContext.assertEquals(MSG1, sendMsg1Response.getMessage().getText());

		GetConversationResponse getConversationResponse1 = null;
		try {
			getConversationResponse1 = defaultApi.getFriendConversation(createAccount2Response.getAccount().getId());
		} catch (ApiException e) {
			testContext.assertTrue(false, "Get valid conversation didn't work: " + e.getMessage());
		}

		testContext.assertEquals(1, getConversationResponse1.getConversation().getMessages().size(),
				"Conversation should have exactly one message");

		testContext.assertEquals(
				sendMsg1Response.getMessage(),
				getConversationResponse1.getConversation().getMessages().get(0),
				"Posted message and conversation message should match");

		//switch directions
		defaultApi.getApiClient().setApiKey(createAccount2Response.getLoginToken());

		//send message 2
		SendMessageResponse sendMsg2Response = null;
		try {
			sendMsg2Response = defaultApi.sendFriendMessage(createAccount1Response.getAccount().getId(), msg2Request);
		} catch (ApiException e) {
			testContext.assertTrue(false, "Sending message (2) to a friend. Got : " + e.getMessage());
		}

		testContext.assertNotNull(sendMsg2Response);
		testContext.assertEquals(createAccount2Response.getAccount().getId(), sendMsg2Response.getMessage().getAuthorId());
		testContext.assertEquals(createAccount2Response.getAccount().getName(), sendMsg2Response.getMessage().getAuthorDisplayName());
		testContext.assertEquals(MSG2, sendMsg2Response.getMessage().getText());

		GetConversationResponse getConversationResponse2 = null;
		try {
			getConversationResponse2 = defaultApi.getFriendConversation(createAccount1Response.getAccount().getId());
		} catch (ApiException e) {
			testContext.assertTrue(false, "Get valid conversation didn't work: " + e.getMessage());
		}

		testContext.assertEquals(2, getConversationResponse2.getConversation().getMessages().size(),
				"Conversation should have 2 messages");

		testContext.assertEquals(
				sendMsg1Response.getMessage(),
				getConversationResponse1.getConversation().getMessages().get(0),
				"Posted message and conversation message (1) should match");

		testContext.assertEquals(
				sendMsg2Response.getMessage(),
				getConversationResponse2.getConversation().getMessages().get(1),
				"Posted message and conversation message (2) should match");

	}


	@Test
	public void testDraftAPI(TestContext context) throws ApiException {
		DefaultApi api = new DefaultApi(new ApiClient().setBasePath(UnityClient.basePath));

		CreateAccountResponse car = api.createAccount(new CreateAccountRequest()
				.name("testuser")
				.email("testuser@hiddenswitch.com")
				.password("testpass"));

		api.getApiClient().setApiKey(car.getLoginToken());

		try {
			api.draftsGet();
		} catch (ApiException e) {
			context.assertEquals(404, e.getCode(), "The exception codes for drafts get do not match.");
		}


		DraftState state = api.draftsPost(new DraftsPostRequest().startDraft(true));
		context.assertEquals(DraftState.StatusEnum.SELECT_HERO, state.getStatus(), "The result of starting a draft is unexpectedly not select hero.");
		try {
			api.draftsChooseCard(new DraftsChooseCardRequest().cardIndex(1));
		} catch (ApiException e) {
			context.assertEquals(400, e.getCode(), "Unexpectedly the client successfully chose a card instead of a hero.");
		}


		state = api.draftsChooseHero(new DraftsChooseHeroRequest().heroIndex(1));
		context.assertNotNull(state.getHeroClass());

		while (state.getCurrentCardChoices() != null
				&& state.getStatus() == DraftState.StatusEnum.IN_PROGRESS) {
			context.assertEquals(3, state.getCurrentCardChoices().size(), "The number of card choices should always be three");
			Entity card = state.getCurrentCardChoices().get(1);
			context.assertNotNull(card, "The draft service should provide a full card definition.");
			context.assertNotNull(card.getCardId(), "The draft service should at least provide a card ID.");
			state = api.draftsChooseCard(new DraftsChooseCardRequest().cardIndex(1));
			context.assertEquals(card.getCardId(), state.getSelectedCards().get(state.getSelectedCards().size() - 1).getCardId(), "The card didn't appear to be selected correctly");
		}

		context.assertEquals(DraftState.StatusEnum.COMPLETE, state.getStatus(), "The status of the draft should be complete.");
		context.assertNotNull(state.getDeckId(), "The draft state should contain a deck ID when it is complete.");

		final String deckId = state.getDeckId();
		vertx.executeBlocking(done -> {
			new UnityClient(context).loginWithUserAccount("testuser", "testpass").matchmakeAndPlayAgainstAI(deckId).waitUntilDone();
			done.handle(Future.succeededFuture());
		}, context.asyncAssertSuccess(then -> {
			DraftState newState = null;
			try {
				newState = api.draftsPost(new DraftsPostRequest().retireEarly(true));
			} catch (ApiException e) {
				context.fail();
			}
			context.assertEquals(DraftState.StatusEnum.RETIRED, newState.getStatus(), "Expected a status of retired.");

			try {
				api.draftsGet();
			} catch (ApiException e) {
				context.assertEquals(404, e.getCode(), "There should be no draft if we retired the draft early.");
			}


			try {
				newState = api.draftsPost(new DraftsPostRequest().startDraft(true));
			} catch (ApiException e) {
				context.fail();
			}
			context.assertEquals(DraftState.StatusEnum.SELECT_HERO, newState.getStatus(), "A draft was not correctly started anew.");
		}));
	}

	@Override
	public void deployServices(Vertx vertx, Handler<AsyncResult<GatewayImpl>> done) {
		System.setProperty("games.defaultNoActivityTimeout", "8000");
		GatewayImpl instance = new GatewayImpl();
		logic = new LogicImpl();
		games = new ClusteredGamesImpl();
		bots = new BotsImpl();
		bots.setBotBehaviour(PlayRandomBehaviour::new);
		matchmaking = new MatchmakingImpl();
		deploy(Arrays.asList(
				games,
				logic,
				bots,
				new AccountsImpl(),
				matchmaking,
				new DecksImpl(),
				new InventoryImpl(),
				new CardsImpl(),
				new DraftImpl()
		), instance, then -> {
			deploymentId = then.result().deploymentID();
			done.handle(then);
		});
	}

}
