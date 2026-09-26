package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import com.watabou.input.KeyEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.PointF;
import org.shatterfish.api.Action;
import org.shatterfish.api.ActionsSection;
import org.shatterfish.api.ItemRef;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.ValidActions;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.RunLogWriter;
import org.shatterfish.harness.driver.UiRole;
import org.shatterfish.harness.executor.ActionExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The record of a human's turns (story 5.9, FR-40, ADR-0013's "Recording human Actions").
 *
 * <p>At every Input wait of a HUMAN Run the embedded Run confirms the wait, reseeds and observes as it
 * does for the Brain, and then hands the wait to this class ({@link #open}) instead of to the executor.
 * The person plays with the game's own input. What they did reaches this class from two places:
 * <ul>
 * <li>hook row 11 ({@link Hooks.HeroInput}): the six methods every hero-directed input converges on,
 * {@code Hero.handle} (with the action it chose), {@code Hero.rest}, {@code Hero.search(true)},
 * {@code Hero.upgradeTalent}, {@code Item.execute} and a targeting {@code CellSelector.select};</li>
 * <li>the Overlay's input lock, which sees the raw tap before the game does, for a window's own button
 * ({@link #pointerUp}: the tap is read against {@link ActionExecutor#optionButtons}, the list the executor
 * presses from, or against a bag window's slots) and for the back key ({@link #keyDown}).</li>
 * </ul>
 *
 * <p>At the end of each frame ({@link #settle}) what was heard becomes one Action: the one the executor
 * would issue to do the same thing, so that a Replay, which is executor-driven, reproduces it. It must
 * be in the wait's valid set, the set the executor itself checks, or the executor would refuse it; one
 * that is not (a click on a distant cell, a throw at an empty cell, an armour ability) is still written
 * as the wait's Action, with an {@code unsupported} record beside it, and nothing after that wait can be
 * reproduced (ADR-0011). Recording announces the hand-over exactly as the executor's own
 * {@code applied()} does, so the wait gate numbers the next wait as it would for the Brain.
 *
 * <p>What this class cannot see is caught rather than missed. A wait that ends with nothing heard, an
 * input heard while no wait was open, and a screen that changed at a wait with no input to explain it
 * (a quickslot assigned, a journal page read) are each an {@code unsupported} record.
 *
 * <p>Threads: every method runs on the UI-role thread, the render thread. A hook heard on any other
 * thread is itself recorded as an input this class could not place.
 */
public final class HumanTurns implements Hooks.HeroInput {

    /** One thing the person did at a wait, in the order it was heard. */
    sealed interface Heard permits Clicked, Targeted, Used, Rested, Searched, Upgraded, Answered, Dismissed, Picked {
    }

    /** {@code Hero.handle}: the cell, the kind of action the game chose for it, and where the hero stood. */
    record Clicked(int cell, String kind, int heroCell) implements Heard {
    }

    /** A targeting selector was handed a cell. */
    record Targeted(int cell) implements Heard {
    }

    /** An item's action, with the reference the wait's Observation gives the item, or null when it gives none. */
    record Used(ItemRef item, String action, String name) implements Heard {
    }

    record Rested(boolean full) implements Heard {
    }

    record Searched() implements Heard {
    }

    record Upgraded(String talent) implements Heard {
    }

    /** A window's own button, by its index in the executor's order. */
    record Answered(int option) implements Heard {
    }

    /** The back key, or anything that sends the window in front away. */
    record Dismissed() implements Heard {
    }

    /** An item chosen in a bag window a used item opened. */
    record Picked(ItemRef item, String name) implements Heard {
    }

    private final RunLogWriter log;
    private final boolean oracle;
    private final Supplier<Observation> observer;
    /** Told of every record written, with the Observation its Action is read against (story 5.4's history). */
    private final java.util.function.BiConsumer<RunLog, Observation> written;

    private long k;
    private long turn;
    private Observation observation;
    private Window openWindow;
    private boolean recorded;
    private final List<Heard> heard = new ArrayList<>();
    private boolean inputThisFrame;
    /** An input heard while no wait was open, written against the next wait opened. */
    private String outside;
    private long unsupportedAt = -1;
    private long unverifiableFrom;
    private String unverifiableWhy = "";
    private long recordedWaits;
    private Action lastAction;

    HumanTurns(RunLogWriter log, boolean oracle, Supplier<Observation> observer,
               java.util.function.BiConsumer<RunLog, Observation> written) {
        this.written = written;
        this.log = log;
        this.oracle = oracle;
        this.observer = observer;
    }

    // --------------------------------------------------------------------------------- the waits

    /**
     * Wait {@code k} is confirmed and observed, and is the person's to take. A previous wait that ended
     * with nothing heard is marked first, and an input heard while no wait was open is marked against
     * this one: the screen this wait shows is not the one a Replay reaches.
     */
    void open(long k, long turn, Observation observation) {
        if (this.k > 0 && !recorded && unsupportedAt != this.k) {
            unsupported(this.k, "wait " + this.k + " ended with no input the recorder heard");
        }
        this.k = k;
        this.turn = turn;
        this.observation = observation;
        this.openWindow = org.shatterfish.harness.driver.Windows.front();
        this.recorded = false;
        heard.clear();
        if (outside != null) {
            unsupported(k, outside);
            outside = null;
        }
    }

    /** Whether wait {@code k} is open: confirmed, and nothing recorded for it yet. */
    boolean isOpen(long k) {
        return this.k == k && k > 0 && !recorded;
    }

    /** Whether a wait is open for the person's input. */
    boolean open() {
        return k > 0 && !recorded;
    }

    /**
     * The end of a frame: if what was heard at the open wait adds up to an input, it is recorded. Runs
     * before the wait gate looks at the frame, so the hand-over it announces is seen in the same frame,
     * as the executor's is.
     */
    void settle(Hero hero, Window front) {
        boolean input = inputThisFrame;
        inputThisFrame = false;
        if (!open() || hero == null) {
            return;
        }
        boolean moved = !HeadlessDriver.heroWaits(hero) || front != openWindow;
        Heard primary = null;
        int primaryAt = -1;
        for (int i = 0; i < heard.size(); i++) {
            Heard h = heard.get(i);
            if (!(h instanceof Targeted) && !(h instanceof Picked)) {
                primary = h;
                primaryAt = i;
            }
        }
        if (primary == null) {
            if (input && !moved && changed()) {
                unsupported(k, "the screen changed at wait " + k + " with no input the executor can express"
                        + " (a quickslot, a journal page, a setting)");
            }
            return;
        }
        Action taken;
        String what;
        switch (primary) {
            case Clicked click -> {
                taken = click(click);
                what = "a click on cell " + click.cell() + " that the game read as " + click.kind();
            }
            case Rested rest -> {
                taken = rest.full() ? new Action.Rest(true) : new Action.Wait();
                what = rest.full() ? "the rest button" : "the wait button";
            }
            case Searched ignored -> {
                taken = new Action.Search();
                what = "the search button";
            }
            case Upgraded upgrade -> {
                taken = new Action.Talent(upgrade.talent());
                what = "a point in " + upgrade.talent();
            }
            case Answered answer -> {
                if (!moved) {
                    return;
                }
                taken = new Action.AnswerPrompt(answer.option());
                what = "option " + answer.option() + " of the window in front";
            }
            case Dismissed ignored -> {
                if (!moved) {
                    return;
                }
                taken = new Action.DismissPrompt();
                what = "the back key on the window in front";
            }
            case Used use -> {
                Integer cell = null;
                Picked pick = null;
                for (int i = primaryAt + 1; i < heard.size(); i++) {
                    if (heard.get(i) instanceof Targeted target && cell == null) {
                        cell = target.cell();
                    } else if (heard.get(i) instanceof Picked picked && pick == null) {
                        pick = picked;
                    }
                }
                if (front instanceof WndBag bag && bag.getSelector() != null) {
                    // The item asked which other item: the person is choosing.
                    return;
                }
                if (!moved && !changed()) {
                    // Nothing yet: a cell selector waiting for its target, or an item that did nothing.
                    heard.removeIf(h -> h instanceof Targeted);
                    return;
                }
                if (use.item() == null) {
                    taken = null;
                    what = use.action() + " of " + use.name() + ", which the wait's Observation does not list";
                } else if (cell != null) {
                    taken = new Action.UseItemAt(use.item(), use.action(), cell);
                    what = use.action() + " of " + use.name() + " at cell " + cell;
                } else if (pick != null) {
                    taken = pick.item() == null ? null : new Action.UseItemOn(use.item(), use.action(), pick.item());
                    what = use.action() + " of " + use.name() + " on " + pick.name();
                } else {
                    taken = new Action.UseItem(use.item(), use.action());
                    what = use.action() + " of " + use.name();
                }
            }
            default -> throw new IllegalStateException("not a primary input: " + primary);
        }
        record(taken, what);
    }

    /**
     * The Action a click is. Every Action the executor takes by clicking is the same call,
     * {@code GameScene.handleCell} on one cell, the hero's own for a pick-up and a transition
     * ({@code ActionExecutor.apply}), and {@code Hero.handle} decides what the click does, so any offered
     * Action that clicks this cell reproduces it: a click on an adjacent heap is the executor's
     * {@code Step} there, which picks the item up as the game's own click does. The game's own choice
     * picks the kind among them when more than one is offered, so the record reads as the person meant
     * it; a click no offered Action makes (a distant cell, which the game walks to over several turns)
     * is the {@code MoveTo} the person made, and unsupported.
     */
    private Action click(Clicked click) {
        int cell = click.cell();
        boolean here = cell == click.heroCell();
        Action meant = switch (click.kind()) {
            case "Move" -> new Action.Step(cell);
            case "Attack" -> new Action.Attack(cell);
            case "Interact" -> new Action.Interact(cell);
            case "PickUp" -> here ? new Action.PickUp() : new Action.Step(cell);
            case "OpenChest" -> new Action.OpenChest(cell);
            case "Buy" -> new Action.Buy(cell);
            case "Unlock" -> new Action.Unlock(cell);
            case "LvlTransition" -> offered(new Action.Ascend()) ? new Action.Ascend() : new Action.Descend();
            default -> new Action.MoveTo(cell);
        };
        if (offered(meant)) {
            return meant;
        }
        for (Action same : here
                ? List.<Action>of(new Action.PickUp(), new Action.Descend(), new Action.Ascend(), new Action.OpenChest(cell),
                        new Action.Buy(cell))
                : List.<Action>of(new Action.Step(cell), new Action.Attack(cell), new Action.Interact(cell),
                        new Action.OpenChest(cell), new Action.Buy(cell), new Action.Unlock(cell))) {
            if (offered(same)) {
                return same;
            }
        }
        return new Action.MoveTo(cell);
    }

    private void record(Action taken, String what) {
        recorded = true;
        heard.clear();
        if (taken != null) {
            written.accept(RunLoop.recordHuman(log, k, turn, observation, taken, oracle), observation);
            recordedWaits++;
            lastAction = taken;
        }
        if (taken == null || !offered(taken)) {
            unsupported(k, what + (taken == null ? "" : ", which the executor does not offer at this wait"));
        }
        // What the executor's applied() says: an Action was handed to the game, so the next wait is new.
        HeadlessDriver.actionHandedOver();
    }

    /** Whether the executor would take {@code action} at the open wait: the set it checks itself. */
    private boolean offered(Action action) {
        ActionsSection offered = observation.actions().actions().isEmpty()
                ? ValidActions.of(observation) : observation.actions();
        return offered.actions().contains(action);
    }

    /** Whether the screen now is not the screen the open wait was observed as; only asked at a wait. */
    private boolean changed() {
        Hero hero = Dungeon.hero;
        if (hero == null || !HeadlessDriver.waitState(hero, org.shatterfish.harness.driver.Windows.front())) {
            return false;
        }
        return !observer.get().hash().equals(observation.hash());
    }

    private void unsupported(long at, String input) {
        RunLog.Unsupported mark = new RunLog.Unsupported(at, input);
        if (log != null) {
            log.write(mark);
        }
        written.accept(mark, null);
        unsupportedAt = at;
        if (unverifiableFrom == 0) {
            unverifiableFrom = at;
            unverifiableWhy = input;
        }
    }

    // ------------------------------------------------------------------------------ what is heard

    private void hear(Heard h, String described) {
        inputThisFrame = true;
        if (UiRole.owner() != Thread.currentThread()) {
            // Not an input the render thread handled; nothing about it can be placed.
            outside = described + ", heard on " + Thread.currentThread().getName();
            return;
        }
        if (!open()) {
            if (outside == null) {
                outside = described + ", heard while no Input wait was open";
            }
            return;
        }
        heard.add(h);
    }

    @Override
    public void cellHandled(int cell) {
        Hero hero = Dungeon.hero;
        HeroAction chosen = hero == null ? null : hero.curAction;
        String kind = chosen == null ? "nothing" : chosen.getClass().getSimpleName();
        hear(new Clicked(cell, kind, hero == null ? -1 : hero.pos), "a click on cell " + cell);
    }

    @Override
    public void cellSelected(int cell) {
        hear(new Targeted(cell), "a target at cell " + cell);
    }

    @Override
    public void itemUsed(Item item, String action) {
        hear(new Used(ref(item), action, item.name()), action + " of " + item.name());
    }

    @Override
    public void rested(boolean full) {
        hear(new Rested(full), full ? "the rest button" : "the wait button");
    }

    @Override
    public void searched() {
        hear(new Searched(), "the search button");
    }

    @Override
    public void talentUpgraded(Talent talent) {
        hear(new Upgraded(talent.title()), "a point in " + talent.title());
    }

    /**
     * A tap released at a screen point, before the game has it (the Overlay's input lock): read against
     * the buttons of the window in front, in the executor's order, or a bag window's slots.
     */
    public void pointerUp(float screenX, float screenY) {
        inputThisFrame = true;
        Window front = org.shatterfish.harness.driver.Windows.front();
        if (front == null || !open()) {
            return;
        }
        if (front instanceof WndBag bag && bag.getSelector() != null) {
            InventorySlot slot = slotAt(bag, screenX, screenY);
            if (slot != null && slot.item() != null) {
                heard.add(new Picked(ref(slot.item()), slot.item().name()));
            }
            return;
        }
        if (front != openWindow) {
            // A window the person opened at this wait (the journal, the hero's info) is not the wait's
            // Prompt, and its buttons are not an answer to anything: the first real HUMAN launch recorded a
            // journal tab as a Prompt answer before this rule.
            return;
        }
        List<Component> buttons = ActionExecutor.optionButtons(front);
        for (int i = 0; i < buttons.size(); i++) {
            Component button = buttons.get(i);
            if (button.isActive() && under(button, screenX, screenY)) {
                heard.add(new Answered(i));
                return;
            }
        }
    }

    /** A key pressed, before the game has it: the back key on a window in front is its dismissal. */
    public void keyDown(int keycode) {
        inputThisFrame = true;
        Window front = org.shatterfish.harness.driver.Windows.front();
        if (!open() || front == null || front != openWindow) {
            // Only the wait's own Prompt is dismissed by the back key; closing a window the person opened is not an Action.
            return;
        }
        if (KeyBindings.getActionForKey(new KeyEvent(keycode, true)) == GameAction.BACK) {
            heard.add(new Dismissed());
        }
    }

    /** Any other input event the Overlay passed to the game this frame, which asks for the screen to be checked. */
    public void inputEvent() {
        inputThisFrame = true;
    }

    private static boolean under(Component button, float screenX, float screenY) {
        Camera camera = button.camera();
        if (camera == null) {
            return false;
        }
        PointF point = camera.screenToCamera((int) screenX, (int) screenY);
        return button.inside(point.x, point.y);
    }

    private static InventorySlot slotAt(Group group, float screenX, float screenY) {
        for (Gizmo member : group.shatterfishMembers()) {
            if (member == null || !member.exists || !member.visible) {
                continue;
            }
            if (member instanceof InventorySlot slot) {
                if (slot.isActive() && under(slot, screenX, screenY)) {
                    return slot;
                }
            } else if (member instanceof Group inner) {
                InventorySlot found = slotAt(inner, screenX, screenY);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * The reference the open wait's Observation gives {@code item}: its place in the hero's belongings,
     * the order the Observer lists them and the executor resolves them in, when the name there agrees.
     */
    private ItemRef ref(Item item) {
        Hero hero = Dungeon.hero;
        if (hero == null || observation == null) {
            return null;
        }
        int index = 0;
        for (Item held : hero.belongings) {
            if (held == item) {
                List<ItemView> items = observation.inventory().items();
                if (index < items.size() && items.get(index).name().equals(item.name())) {
                    return new ItemRef(index, items.get(index).name(), items.get(index).quantity());
                }
                return null;
            }
            index++;
        }
        return null;
    }

    // ------------------------------------------------------------------------------ what is known

    /** The first wait from which nothing can be reproduced, or 0. */
    long unverifiableFrom() {
        return unverifiableFrom;
    }

    /** Why, or empty. */
    String unverifiableWhy() {
        return unverifiableWhy;
    }

    /** Waits whose Action was recorded. */
    long recordedWaits() {
        return recordedWaits;
    }

    /** The last Action recorded, or null. */
    Action lastAction() {
        return lastAction;
    }

    /** The open wait's index, or 0; the Observation it was confirmed with. */
    long openWait() {
        return open() ? k : 0;
    }
}
