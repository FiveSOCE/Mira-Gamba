package com.mira.gamba.service;

import com.mira.gamba.MiraGambaPlugin;
import com.mira.gamba.model.CardMachine;
import com.mira.gamba.model.CardSuit;
import com.mira.gamba.model.PlayingCard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CardGameService {
    private final MiraGambaPlugin plugin;
    private final CardMachineService machines;
    private final EconomyService economy;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public CardGameService(MiraGambaPlugin plugin, CardMachineService machines, EconomyService economy) {
        this.plugin = plugin;
        this.machines = machines;
        this.economy = economy;
    }

    public void press(Player player, CardMachine machine, String action) {
        Session session = sessions.get(machine.id());

        if (session == null) {
            if (!action.equals("RED") && !action.equals("BLACK")) {
                plugin.msg(player, "cards.messages.start");
                return;
            }

            if (!economy.has(player, machine.bet()) || !economy.withdraw(player, machine.bet())) {
                plugin.msg(player, "messages.no-money", "%amount%", economy.format(machine.bet()));
                return;
            }

            session = new Session(player.getUniqueId(), freshDeck(), new ArrayList<>(), 0);
            sessions.put(machine.id(), session);
        } else if (!session.playerId().equals(player.getUniqueId())) {
            plugin.msg(player, "cards.messages.busy");
            return;
        }

        switch (session.stage()) {
            case 0 -> resolveColor(player, machine, session, action);
            case 1 -> resolveHigherLower(player, machine, session, action);
            case 2 -> resolveInsideOutside(player, machine, session, action);
            case 3 -> resolveSuit(player, machine, session, action);
            default -> sessions.remove(machine.id());
        }
    }

    private void resolveColor(Player player, CardMachine machine, Session session, String action) {
        if (!action.equals("RED") && !action.equals("BLACK")) {
            plugin.msg(player, "cards.messages.color");
            return;
        }

        PlayingCard card = draw(session);
        reveal(machine, 0, card);
        boolean guessedRed = action.equals("RED");
        boolean correct = card.suit().red() == guessedRed;

        if (!correct) {
            lose(player, machine, card);
            return;
        }

        session.stage(1);
        plugin.msg(player, "cards.messages.correct", "%card%", card.displayName());
        plugin.msg(player, "cards.messages.higher-lower");
    }

    private void resolveHigherLower(Player player, CardMachine machine, Session session, String action) {
        if (!action.equals("HIGHER") && !action.equals("LOWER")) {
            plugin.msg(player, "cards.messages.higher-lower");
            return;
        }

        PlayingCard previous = session.drawn().get(0);
        PlayingCard card = draw(session);
        reveal(machine, 1, card);

        boolean correct = action.equals("HIGHER")
                ? card.value() > previous.value()
                : card.value() < previous.value();

        if (!correct) {
            lose(player, machine, card);
            return;
        }

        session.stage(2);
        plugin.msg(player, "cards.messages.correct", "%card%", card.displayName());
        plugin.msg(player, "cards.messages.inside-outside");
    }

    private void resolveInsideOutside(Player player, CardMachine machine, Session session, String action) {
        if (!action.equals("INSIDE") && !action.equals("OUTSIDE")) {
            plugin.msg(player, "cards.messages.inside-outside");
            return;
        }

        PlayingCard first = session.drawn().get(0);
        PlayingCard second = session.drawn().get(1);
        PlayingCard card = draw(session);
        reveal(machine, 2, card);

        int low = Math.min(first.value(), second.value());
        int high = Math.max(first.value(), second.value());
        boolean inside = card.value() > low && card.value() < high;
        boolean outside = card.value() < low || card.value() > high;
        boolean correct = action.equals("INSIDE") ? inside : outside;

        if (!correct) {
            lose(player, machine, card);
            return;
        }

        session.stage(3);
        plugin.msg(player, "cards.messages.correct", "%card%", card.displayName());
        plugin.msg(player, "cards.messages.suit");
    }

    private void resolveSuit(Player player, CardMachine machine, Session session, String action) {
        CardSuit guessed;
        try {
            guessed = CardSuit.valueOf(action);
        } catch (IllegalArgumentException ex) {
            plugin.msg(player, "cards.messages.suit");
            return;
        }

        PlayingCard card = draw(session);
        reveal(machine, 3, card);

        if (card.suit() != guessed) {
            lose(player, machine, card);
            return;
        }

        double multiplier = plugin.getConfig().getDouble("cards.win-multiplier", 10.0D);
        double payout = machine.bet() * multiplier;
        economy.deposit(player, payout);
        sessions.remove(machine.id());

        plugin.msg(
                player,
                "cards.messages.win",
                "%amount%",
                economy.format(payout),
                "%card%",
                card.displayName()
        );
        player.getWorld().playSound(machine.origin(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
    }

    private void lose(Player player, CardMachine machine, PlayingCard card) {
        sessions.remove(machine.id());
        plugin.msg(player, "cards.messages.lose", "%card%", card.displayName());
        player.getWorld().playSound(machine.origin(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
    }

    private PlayingCard draw(Session session) {
        PlayingCard card = session.deck().remove(session.deck().size() - 1);
        session.drawn().add(card);
        return card;
    }

    private List<PlayingCard> freshDeck() {
        List<PlayingCard> deck = new ArrayList<>(52);
        for (CardSuit suit : CardSuit.values()) {
            for (int value = 2; value <= 14; value++) {
                deck.add(new PlayingCard(value, suit));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    private void reveal(CardMachine machine, int index, PlayingCard card) {
        List<ItemFrame> frames = machines.frames(machine);
        if (index < 0 || index >= frames.size()) return;

        ItemStack item = new ItemStack(card.suit().material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
                card.displayName(),
                card.suit().red() ? NamedTextColor.RED : NamedTextColor.DARK_GRAY
        ));
        item.setItemMeta(meta);

        frames.get(index).setItem(item, false);
    }

    private static final class Session {
        private final UUID playerId;
        private final List<PlayingCard> deck;
        private final List<PlayingCard> drawn;
        private int stage;

        private Session(UUID playerId, List<PlayingCard> deck, List<PlayingCard> drawn, int stage) {
            this.playerId = playerId;
            this.deck = deck;
            this.drawn = drawn;
            this.stage = stage;
        }

        UUID playerId() { return playerId; }
        List<PlayingCard> deck() { return deck; }
        List<PlayingCard> drawn() { return drawn; }
        int stage() { return stage; }
        void stage(int stage) { this.stage = stage; }
    }
}
