package plugin.devkit

import com.google.inject.{Binder, Inject, Singleton}
import net.runelite.api.Client
import net.runelite.api.events.{ScriptCallbackEvent, ScriptPostFired, ScriptPreFired}
import net.runelite.api.widgets.Widget
import net.runelite.client.callback.ClientThread
import net.runelite.client.eventbus.EventBus
import net.runelite.client.plugins.{Plugin, PluginDescriptor}
import net.runelite.client.ui.overlay.OverlayManager
import org.slf4j.{Logger, LoggerFactory}

import scala.util.chaining.*
import scala.reflect.{ClassTag, TypeTest, Typeable}

@PluginDescriptor(
  name = "Script Inspector",
  enabledByDefault = true,
  description = "Inspect the Script VM during script execution",
  tags = Array("develop", "debug", "script", "inspect", "cs2", "rs2asm")
)
class ScriptInspector() extends Plugin {
  private val log: Logger = LoggerFactory.getLogger(classOf[ScriptInspector]);
  lazy val client: Client = getInjector.getInstance(classOf[Client])
  lazy val overlayManager: OverlayManager = getInjector.getInstance(classOf[OverlayManager])
  lazy val clientThread: ClientThread = getInjector.getInstance(classOf[ClientThread])
  lazy val eventBus: EventBus = getInjector.getInstance(classOf[EventBus])

//  override def configure(binder: Binder): Unit = {}
    var subs: Seq[EventBus.Subscriber] = Seq.empty

  override protected def startUp(): Unit = {
    log.debug("Script Inspector Startup")

    subs = List(
      eventBus.register(classOf[ScriptPreFired], onScriptPreFired, 0),
      eventBus.register(classOf[ScriptPostFired], onScriptPostFired, 0),
      eventBus.register(classOf[ScriptCallbackEvent], onScriptCallback, 0)
    )
  }

  override protected def shutDown(): Unit = {
    subs.foreach(eventBus.unregister)
    subs = Seq.empty
    log.debug("Script Inspector Shutdown")
  }

  case class ScriptEventData(op: Int, opbase: String, source: Widget, mouse: (Int, Int), key: (Int, Char), args: List[AnyRef])
  case class ScriptPreFiredData(scriptId: Int, data: Option[ScriptEventData])
  private def onScriptPreFired(event: ScriptPreFired): Unit = {
    val converted = ScriptPreFiredData(event.getScriptId,
        Option.apply(event.getScriptEvent).map(p => {
          ScriptEventData(p.getOp, p.getOpbase, p.getSource, p.getMouseX -> p.getMouseY, p.getTypedKeyCode -> p.getTypedKeyChar.toChar, p.getArguments.toList)
        })
    )
    if(converted.data.isDefined) log.info("{}", converted)
    else log.debug("{}", converted)
  }

  private def onScriptPostFired(event: ScriptPostFired): Unit = {
    log.debug("{}", event)
  }

  private def onScriptCallback(event: ScriptCallbackEvent): Unit = {
    log.debug("{}", event)
  }
  //  @Inject private val client: Client
//  @Inject private val overlayManager: OverlayManager
//  @Inject private val clientThread: ClientThread
}
