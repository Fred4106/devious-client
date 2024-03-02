package net.runelite.devkit

import net.runelite.client.RuneLite
import net.runelite.client.externalplugins.ExternalPluginManager
import plugin.devkit.ScriptInspector

object Launcher {
  def main(args: Array[String]): Unit = {
    println(args.toList)
    ExternalPluginManager.loadBuiltin(classOf[ScriptInspector])
    RuneLite.main(args)
  }
}
