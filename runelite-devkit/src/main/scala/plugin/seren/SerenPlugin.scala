package plugin.seren

import net.unethicalite.api.entities.*
import com.google.inject.{Binder, Inject, Singleton}
import net.runelite.api.{Actor, ChatMessageType, Client, Item, ItemID, Locatable, NPC, NpcID, Player, Skill, Varbits}
import net.runelite.api.events.{AnimationChanged, GameTick, NpcActionChanged, NpcDespawned, NpcSpawned, ScriptCallbackEvent, ScriptPostFired, ScriptPreFired}
import net.runelite.client.config.Keybind
import net.runelite.client.input.KeyManager
import net.runelite.client.util.HotkeyListener
import net.unethicalite.api.magic.{Magic, Spell, SpellBook}
import net.unethicalite.api.query.entities.NPCQuery

import java.awt.event.KeyEvent
import java.util.function.Supplier
//import net.runelite.api.queries.{ActorQuery, LocatableQuery, NPCQuery}
import net.runelite.api.widgets.Widget
import net.runelite.cache.definitions.NpcDefinition
import net.runelite.client.callback.ClientThread
import net.runelite.client.eventbus.{EventBus, Subscribe}
import net.runelite.client.game.npcoverlay.{HighlightedNpc, NpcOverlayService}
import net.runelite.client.plugins.grounditems.config.HighlightTier
import net.runelite.client.plugins.{Plugin, PluginDescriptor}
import net.runelite.client.ui.overlay.OverlayManager
import net.unethicalite.api.plugins.LoopedPlugin
import net.unethicalite.api.query.items.ItemQuery
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.*
import java.awt.Color
import scala.reflect.{TypeTest, Typeable}
import scala.util.chaining.*

@PluginDescriptor(
  name = "Seren Helper",
  enabledByDefault = false,
  description = "Helps manage the seren fight",
  tags = Array("quest", "boss", "Seren")
)
class SerenPlugin() extends LoopedPlugin {
  private val log: Logger = LoggerFactory.getLogger(classOf[SerenPlugin])
  lazy val client: Client = getInjector.getInstance(classOf[Client])
  lazy val overlayManager: OverlayManager = getInjector.getInstance(classOf[OverlayManager])
  lazy val clientThread: ClientThread = getInjector.getInstance(classOf[ClientThread])
  lazy val eventBus: EventBus = getInjector.getInstance(classOf[EventBus])
  lazy val npcOverlayService: NpcOverlayService = getInjector.getInstance(classOf[NpcOverlayService])
  lazy val keyManager: KeyManager = getInjector.getInstance(classOf[KeyManager])


  extension (n: NPCQuery) {
    def get(): List[NPC] = {
      n.results().sortedByDistance().asScala.toList
    }
  }

  extension (n: ItemQuery) {
    def get(): List[Item] = {
      n.results().asScala.toList
    }
  }
  inline def npcQuery(inline ids: Int *): NPCQuery = {
    NPCs.query().ids(ids*)
//    _.andThen(_.results().asScala.toList)
  }

  sealed trait ItemQuerySource {
    def query: ItemQuery
  }
  object ItemQuerySource {
    case object Inventory extends ItemQuerySource{
      override def query: ItemQuery = net.unethicalite.api.items.Inventory.query()
    }
    case object Bank extends ItemQuerySource {
      case object Inventory extends ItemQuerySource {
        override def query: ItemQuery = net.unethicalite.api.items.Bank.Inventory.query()
      }

      override def query: ItemQuery = net.unethicalite.api.items.Bank.query()
    }
    case object Equipment extends ItemQuerySource {
      override def query: ItemQuery = net.unethicalite.api.items.Equipment.query()
    }
  }

  def itemQuery(ids: Int *): ItemQuerySource => ItemQuery = _.query.ids(ids *)

  val serenQuery: NPCQuery = npcQuery(NpcID.FRAGMENT_OF_SEREN)
  val serenFakeQuery = npcQuery(NpcID.FRAGMENT_OF_SEREN_8918)
  val serenHealerQuery = npcQuery(NpcID.CRYSTAL_WHIRLWIND)

  private val phoenixNecklaceQuery: ItemQuerySource => Option[Item] = itemQuery(ItemID.PHOENIX_NECKLACE).andThen(_.results().asScala.toList.headOption)
  private val staffQuery: ItemQuerySource => Option[Item] = itemQuery(ItemID.WARPED_SCEPTRE).andThen(_.results().asScala.toList.headOption)
  private val bookQuery: ItemQuerySource => Option[Item] = itemQuery(ItemID.BOOK_OF_THE_DEAD).andThen(_.results().asScala.toList.headOption)
  private val bowQuery: ItemQuerySource => Option[Item] = itemQuery(ItemID.MAGIC_SHORTBOW_I).andThen(_.results().asScala.toList.headOption)

  private val restorePotionQuery: ItemQuery = itemQuery(ItemID.SUPER_RESTORE1, ItemID.SUPER_RESTORE2, ItemID.SUPER_RESTORE3, ItemID.SUPER_RESTORE4)(ItemQuerySource.Inventory)
  private val nightShadeQuery: ItemQuery = itemQuery(ItemID.NIGHTSHADE)(ItemQuerySource.Inventory)
  private val rockCakeQuery: ItemQuery = itemQuery(ItemID.DWARVEN_ROCK_CAKE)(ItemQuerySource.Inventory)


  //
  //  extension [QRT <: QueryResults[E]: Typeable, E <: AnyRef: Typeable](c: Query[E, _, QRT]) {
  //    def scalaResults: List[E] = c.result(client).asScala.toList
  //  }
  //  extension [QRT <: LocatableQueryResults[E] : Typeable, E <: AnyRef & Locatable : Typeable](c: Query[E, _, QRT]) {
  //    def resultsByDistance(x: Locatable): List[E] = c.result(client).asScala.toList.sortBy(_.distanceTo(x))
  //  }

//  val queryColorMap: Map[NPCQuery, (HighlightedNpc.HighlightedNpcBuilder) => HighlightedNpc.HighlightedNpcBuilder] = Map(
//    serenQuery -> ((hb: HighlightedNpc.HighlightedNpcBuilder) => hb.highlightColor(Color.GREEN).render(_ => serenHealerQuery.results().isEmpty)),
//    serenFakeQuery -> ((hb: HighlightedNpc.HighlightedNpcBuilder) => hb.highlightColor(Color.RED)),
//    serenHealerQuery -> ((hb: HighlightedNpc.HighlightedNpcBuilder) => hb.highlightColor(Color.ORANGE))
//  )

  private val scuffedEatKey = new HotkeyListener(() => new Keybind(KeyEvent.VK_S, 0)) {
    override def hotkeyPressed(): Unit = {
      clientThread.invoke(() => {
        if(phoenixNecklaceQuery(ItemQuerySource.Equipment).isDefined){
          val nightShade = nightShadeQuery
          val rockCake = rockCakeQuery
          if (client.getBoostedSkillLevel(Skill.HITPOINTS) > 30) {
            rockCake.get().headOption.foreach(_.interact("Guzzle"))
          } else if (client.getBoostedSkillLevel(Skill.HITPOINTS) < 31 && client.getBoostedSkillLevel(Skill.HITPOINTS) > 16) {
            nightShade.get().headOption.foreach(_.interact("Eat"))
          }
        } else {
          phoenixNecklaceQuery(ItemQuerySource.Inventory).tapEach(_.interact("Wear"))
        }
        ()
      })
    }
  }


  private val switchBow = new HotkeyListener(() => new Keybind(KeyEvent.VK_F, 0)) {
    override def hotkeyPressed(): Unit = {
      clientThread.invoke(() => {
        if(bowQuery(ItemQuerySource.Equipment).isEmpty) {
          bowQuery(ItemQuerySource.Inventory).foreach(_.interact("Wield"))
        } else {
          if (staffQuery(ItemQuerySource.Equipment).isEmpty) {
            staffQuery(ItemQuerySource.Inventory).foreach(_.interact("Wield"))
          }
          if (bookQuery(ItemQuerySource.Equipment).isEmpty) {
            bookQuery(ItemQuerySource.Inventory).foreach(_.interact("Wield"))
          }
        }
        ()
      })
    }
  }
  private val bloodSpell = new HotkeyListener(() => new Keybind(KeyEvent.VK_SPACE, 0)) {
    override def hotkeyPressed(): Unit = {

      clientThread.invoke(() => {
        if(staffQuery(ItemQuerySource.Equipment).isDefined) {
          serenQuery.get().headOption.foreach(Magic.cast(SpellBook.Ancient.BLOOD_BLITZ, _))
        }
      })
    }
  }


  def highlighter(n: NPC): HighlightedNpc = {
    (n match {
      case seren if serenQuery.test(seren) => Some((_: HighlightedNpc.HighlightedNpcBuilder).highlightColor(Color.GREEN).render(_ => serenHealerQuery.results().isEmpty))
      case fake if serenFakeQuery.test(fake) => Some((_: HighlightedNpc.HighlightedNpcBuilder).highlightColor(Color.RED))
      case healer if serenHealerQuery.test(healer) => Some((_: HighlightedNpc.HighlightedNpcBuilder).highlightColor(Color.ORANGE))
      case _ => Option.empty
    }).map(_.andThen(_.build()).apply((HighlightedNpc.builder().hull(true).npc(n)))).orNull
  }

  override protected def startUp(): Unit = {
    log.debug("Script Inspector Startup")
    npcOverlayService.registerHighlighter(highlighter(_))
    eventBus.register(this)

    keyManager.registerKeyListener(scuffedEatKey);
    keyManager.registerKeyListener(switchBow);
    keyManager.registerKeyListener(bloodSpell);
  }

  override protected def shutDown(): Unit = {
    log.debug("Script Inspector Shutdown")
    eventBus.unregister(this)
    npcOverlayService.unregisterHighlighter(highlighter(_))
    keyManager.unregisterKeyListener(scuffedEatKey);
    keyManager.unregisterKeyListener(switchBow);
    keyManager.unregisterKeyListener(bloodSpell);
  }

  @Subscribe
  private def onNpcDespawned(npcDespawned: NpcDespawned): Unit = {
    npcDespawned.getNpc match {
      case npc if (serenQuery.test(npc)) => log.debug("seren despawned at {} with composition {}", npc.getWorldLocation, npc.getComposition)
      case npc if (serenFakeQuery.test(npc)) => log.trace("fake despawned spawned at {} with composition {}", npc.getWorldLocation, npc.getComposition)
      case npc if (serenHealerQuery.test(npc)) => log.trace("healer despawned at {} with composition {}", npc.getWorldLocation, npc.getComposition)
      case _ =>
    }
  }

  @Subscribe
  private def onNpcSpawned(spawn: NpcSpawned): Unit = {
    spawn.getNpc match {
      case npc if (serenQuery.test(npc)) => log.debug("seren spawned at {} with composition {}", npc.getWorldLocation, npc.getComposition)
      case npc if (serenFakeQuery.test(npc)) => log.trace("fake seren spawned at {} with composition {}", npc.getWorldLocation, npc.getComposition)
      case npc if (serenHealerQuery.test(npc)) => log.trace("healer spawned at {} with composition {}", npc.getWorldLocation, npc.getComposition)
      case _ =>
    }
  }


  @Subscribe
  private def onActorAnimChanged(event: AnimationChanged): Unit = {
    event.getActor match {
      case npc: NPC if (serenQuery.test(npc)) => log.debug("seren animation changed {}", npc.getAnimation)
      case npc: NPC if (serenFakeQuery.test(npc)) => log.trace("fake seren animation changed {}", npc.getAnimation)
      case npc: NPC if (serenHealerQuery.test(npc)) => log.trace("healer animation changed {}", npc.getAnimation)
      case player: Player if (player == client.getLocalPlayer) => log.debug("player animation changed {}", player.getAnimation)
      case o =>
    }
  }

  var healthFlaggedLow = false
  var healthFlaggedHigh = false

  var prayerFlaggedLow = false

  @Subscribe
  private def onGameTick(event: GameTick): Unit = {
    if (client.getBoostedSkillLevel(Skill.HITPOINTS) > 28) {
      if (!healthFlaggedHigh) {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "seren", "high health", null)
        healthFlaggedHigh = true;
      }
    } else if (healthFlaggedHigh) {
      healthFlaggedHigh = false;
    }
    if (client.getBoostedSkillLevel(Skill.HITPOINTS) <= 21) {
      if (!healthFlaggedLow) {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "seren", "low health", null)
        healthFlaggedLow = true;
      }
    } else if (healthFlaggedLow) {
      healthFlaggedLow = false;
    }

    if (client.getBoostedSkillLevel(Skill.PRAYER) <= 21) {
      if (!prayerFlaggedLow) {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "seren", "low prayer", null)
        prayerFlaggedLow = true;
      }
    } else if (prayerFlaggedLow) {
      prayerFlaggedLow = false;
    }
  }

//  val prayerPotQuery: ItemQuery = Inv.query().ids(ItemID.SUPER_RESTORE1, ItemID.SUPER_RESTORE2, ItemID.SUPER_RESTORE3, ItemID.SUPER_RESTORE4).noted(false)



  override protected def loop(): Int = {
    clientThread.invoke( () => {
      if (prayerFlaggedLow) {
        restorePotionQuery.get().foreach(_.interact("Drink"))
      }
      else if (healthFlaggedLow) {
        serenQuery.get().headOption.foreach(
          seren => Magic.cast(SpellBook.Ancient.BLOOD_BLITZ, seren)
        )
      }
    })
    100
  }
}
