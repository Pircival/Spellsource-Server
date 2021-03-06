package com.hiddenswitch.spellsource;

import net.demilich.metastone.game.actions.GameAction;
import net.demilich.metastone.game.cards.Card;
import net.demilich.metastone.game.entities.heroes.Hero;
import net.demilich.metastone.game.entities.minions.Minion;
import net.demilich.metastone.tests.util.TestBase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Objects;

import static org.testng.Assert.*;

public class MusicianTests extends TestBase {

	@Test
	public void testChord() {
		runGym((context, player, opponent) -> {
			Hero musician = player.getHero();
			GameAction chord = musician.getHeroPower().play();
			context.performAction(context.getPlayer1().getId(), chord);
			assertEquals(player.getMinions().size(), 1);
			Minion patron = player.getMinions().get(0);
			assertEquals(patron.getSourceCard().getCardId(), "token_patron");

			chord.setTarget(patron);
			context.performAction(context.getPlayer1().getId(), chord);
			Assert.assertEquals(patron.getHp(), 2);
			Assert.assertEquals(patron.getAttack(), 2);

			context.endTurn();
			context.endTurn();

			assertTrue(context.getValidActions().stream().anyMatch(action -> patron.getReference().equals(action.getTargetReference())));
		}, "CORAL", "ANY");

		runGym((context, player, opponent) -> {
			Minion helper = playMinionCard(context, player, "minion_little_helper");
			player.setMana(10);
			Card helped = player.getHand().get(0);
			destroy(context, helper);
			assertEquals(player.getMinions().size(), 0);
			player.setMana(10);
			assertTrue(context.getValidActions().stream().anyMatch(ga -> Objects.equals(ga.getSourceReference(), helped.getReference())
					&& Objects.isNull(ga.getTargetReference())));
			assertTrue(context.getValidActions().stream().noneMatch(ga -> Objects.equals(ga.getSourceReference(), helped.getReference())
					&& !Objects.isNull(ga.getTargetReference())));
			Minion target = playMinionCard(context, player, "minion_neutral_test");
			assertEquals(player.getMinions().size(), 1);
			player.setMana(10);
			assertTrue(context.getValidActions().stream().noneMatch(ga -> Objects.equals(ga.getSourceReference(), helped.getReference())
					&& Objects.isNull(ga.getTargetReference())));
			assertTrue(context.getValidActions().stream().anyMatch(ga -> Objects.equals(ga.getSourceReference(), helped.getReference()) && Objects.equals(ga.getTargetReference(), target.getReference())));
			destroy(context, target);
			assertEquals(player.getMinions().size(), 0);
			player.setMana(10);
			assertTrue(context.getValidActions().stream().anyMatch(ga -> Objects.equals(ga.getSourceReference(), helped.getReference())
					&& Objects.isNull(ga.getTargetReference())));
			assertTrue(context.getValidActions().stream().noneMatch(ga -> Objects.equals(ga.getSourceReference(), helped.getReference())
					&& !Objects.isNull(ga.getTargetReference())));
		}, "CORAL", "ANY");
	}

	@Test
	public void testRehearsal() {
		runGym((context, player, opponent) -> {
			playCard(context, player, "spell_rehearsal");
			context.endTurn();
			context.endTurn();
			receiveCard(context, player, "spell_groupies");
			player.setMana(4);
			playCard(context, player, "spell_groupies");
			assertEquals(player.getMana(), 4);
			playCard(context, player, "spell_test_deal_3_to_all");
			assertEquals(player.getMana(), 0);
		});

		runGym((context, player, opponent) -> {
			playCard(context, player, "spell_rehearsal");
			context.endTurn();
			context.endTurn();
			context.endTurn();
			context.endTurn();
			receiveCard(context, player, "spell_groupies");
			player.setMana(4);
			playCard(context, player, "spell_groupies");
			assertEquals(player.getMana(), 0);
		});
	}

	@Test
	public void testSpiritedAnnouncer() {
		runGym((context, player, opponent) -> {
			playCard(context, player, "minion_spirited_announcer");
			context.endTurn();
			context.endTurn();
			receiveCard(context, player, "minion_spirited_announcer");
			player.setMana(6);
			playCard(context, player, "minion_spirited_announcer");
			assertEquals(player.getMana(), 6);
			playCard(context, player, "minion_spirited_announcer");
			assertEquals(player.getMana(), 0);
		});

		runGym((context, player, opponent) -> {
			playCard(context, player, "minion_spirited_announcer");
			context.endTurn();
			context.endTurn();
			context.endTurn();
			context.endTurn();
			receiveCard(context, player, "minion_spirited_announcer");
			player.setMana(6);
			playCard(context, player, "minion_spirited_announcer");
			assertEquals(player.getMana(), 0);
		});
	}

	@Test
	public void testMyraTheMasterful() {
		runGym((context, player, opponent) -> {
			Minion myra = playMinionCard(context, player, "minion_myra_the_masterful");
			playCard(context, player, "spell_test_deal_5_to_enemy_hero");
			assertEquals(opponent.getHero().getHp(), opponent.getHero().getMaxHp() - 10);
			context.endTurn();

			player.getHero().setHp(30);
			playCard(context, opponent, "spell_test_deal_5_to_enemy_hero");
			assertEquals(player.getHero().getHp(), player.getHero().getMaxHp() - 5);
			context.endTurn();

			destroy(context, myra);
			opponent.getHero().setHp(30);
			playCard(context, player, "spell_test_deal_5_to_enemy_hero");
			assertEquals(opponent.getHero().getHp(), opponent.getHero().getMaxHp() - 5);
		});
	}

	@Test
	public void testAlluringMinstrel() {
		runGym((context, player, opponent) -> {
			playCard(context, player, "minion_alluring_minstrel");
			assertEquals(player.getHand().size(), 0);
			context.endTurn();
			assertEquals(player.getHand().size(), 1);
			assertEquals(player.getHand().get(0).getSourceCard().getCardId(), "spell_lunstone");
			context.endTurn();
			assertEquals(player.getHand().size(), 1);
			assertEquals(player.getHand().get(0).getSourceCard().getCardId(), "spell_lunstone");
		});
	}

	@Test
	public void testCadenza() {
		runGym((context, player, opponent) -> {
			Minion cadenza = playMinionCard(context, player, "minion_cadenza");
			assertEquals(cadenza.getAttack(), cadenza.getBaseAttack());
			playCard(context, player, "minion_composer");
			playCard(context, player, "minion_composer");
			assertEquals(cadenza.getAttack(), cadenza.getBaseAttack() + 2);
			playCard(context, player, "spell_test_deal_3_to_all");
			assertEquals(cadenza.getAttack(), cadenza.getBaseAttack());
		});
	}

	@Test
	public void testFrazzledBarmaid() {
		runGym((context, player, opponent) -> {
			playCard(context, player, "minion_frazzled_barmaid");
			player.getHero().setHp(20);
			playCard(context, player, "spell_lunstone");
			assertEquals(player.getHero().getHp(), 24);
			playCard(context, opponent, "spell_lunstone");
			assertEquals(player.getHero().getHp(), 24);
			playCard(context, player, "spell_test_deal_5_to_enemy_hero");
			assertEquals(player.getHero().getHp(), 24);
		});
	}

	@Test
	public void testStreetPerformerCoral() {
		runGym((context, player, opponent) -> {
			playCard(context, player, "minion_street_performer_coral");
			playCard(context, player, "spell_lunstone");
			Minion composer1 = playMinionCard(context, player, "minion_composer");
			Minion composer2 = playMinionCard(context, player, "minion_composer");

			playCard(context, player, "spell_lunstone");
			assertEquals(composer1.getAttack(), composer1.getBaseAttack() + 1);
			assertEquals(composer1.getHp(), composer1.getBaseHp() + 1);
			assertEquals(composer2.getAttack(), composer2.getBaseAttack() + 1);
			assertEquals(composer2.getHp(), composer2.getBaseHp() + 1);

			playCard(context, opponent, "spell_lunstone");
			assertEquals(composer1.getAttack(), composer1.getBaseAttack() + 1);
			assertEquals(composer1.getHp(), composer1.getBaseHp() + 1);
			assertEquals(composer2.getAttack(), composer2.getBaseAttack() + 1);
			assertEquals(composer2.getHp(), composer2.getBaseHp() + 1);

			playCard(context, player, "spell_test_deal_5_to_enemy_hero");
			assertEquals(composer1.getAttack(), composer1.getBaseAttack() + 1);
			assertEquals(composer1.getHp(), composer1.getBaseHp() + 1);
			assertEquals(composer2.getAttack(), composer2.getBaseAttack() + 1);
			assertEquals(composer2.getHp(), composer2.getBaseHp() + 1);
		});
	}

	@Test
	public void testCharm() {
		runGym((context, player, opponent) -> {
			Minion composer1 = playMinionCard(context, opponent, "minion_composer");
			Minion composer2 = playMinionCard(context, opponent, "minion_composer");
			Minion composer3 = playMinionCard(context, opponent, "minion_composer");

			playCard(context, opponent, "spell_charm");

			assertEquals(composer1.getAttack(), composer1.getBaseAttack());
			assertEquals(composer2.getAttack(), composer2.getBaseAttack());
			assertEquals(composer3.getAttack(), composer3.getBaseAttack());

			playCard(context, player, "spell_charm");

			assertEquals(composer1.getAttack(), composer1.getBaseAttack() - 2);
			assertEquals(composer2.getAttack(), composer2.getBaseAttack() - 2);
			assertEquals(composer3.getAttack(), composer3.getBaseAttack() - 2);

			context.endTurn();

			assertEquals(composer1.getAttack(), composer1.getBaseAttack() - 2);
			assertEquals(composer2.getAttack(), composer2.getBaseAttack() - 2);
			assertEquals(composer3.getAttack(), composer3.getBaseAttack() - 2);

			context.endTurn();

			assertEquals(composer1.getAttack(), composer1.getBaseAttack());
			assertEquals(composer2.getAttack(), composer2.getBaseAttack());
			assertEquals(composer3.getAttack(), composer3.getBaseAttack());
		});
	}

	@Test
	public void testFullAudience() {
		runGym((context, player, opponent) -> {
			Card playerComposer1 = receiveCard(context, player, "minion_composer");
			Card playerComposer2 = receiveCard(context, player, "minion_composer");
			Card opponentComposer1 = receiveCard(context, opponent, "minion_composer");
			Card opponentComposer2 = receiveCard(context, opponent, "minion_composer");
			Card randomSpell = receiveCard(context, player, "spell_fortissimo");

			playCard(context, player, "spell_full_audience");

			assertEquals(costOf(context, player, playerComposer1), playerComposer1.getBaseManaCost() - 1);
			assertEquals(costOf(context, player, playerComposer2), playerComposer2.getBaseManaCost() - 1);
			assertEquals(costOf(context, player, opponentComposer1), opponentComposer1.getBaseManaCost());
			assertEquals(costOf(context, player, opponentComposer2), opponentComposer2.getBaseManaCost());
			assertEquals(costOf(context, player, randomSpell), randomSpell.getBaseManaCost());
		});
	}

	@Test
	public void testPublicPerformance() {
		runGym((context, player, opponent) -> {
			Minion composer = playMinionCard(context, player, "minion_composer");
			playCard(context, player, "spell_public_performance", composer);
			assertEquals(composer.getAttack(), composer.getBaseAttack() + 1);
			assertEquals(composer.getHp(), composer.getBaseHp() + 1);
			assertEquals(player.getHand().size(), 2);
			assertEquals(player.getHand().get(0).getSourceCard().getCardId(), "spell_lunstone");
			assertEquals(player.getHand().get(1).getSourceCard().getCardId(), "spell_lunstone");
		});
	}
}
