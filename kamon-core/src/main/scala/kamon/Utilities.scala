package kamon

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor}

import com.typesafe.config.Config
import kamon.util.{Clock, Filter}

import scala.collection.concurrent.TrieMap

/**
  * Base utilities used by other Kamon components.
  */
trait Utilities { self: Configuration =>
  private val _clock = new Clock.Anchored()
  private val _scheduler = Executors.newScheduledThreadPool(1, numberedThreadFactory("kamon-scheduler", daemon = false))
  private val _filters = TrieMap.empty[String, Filter]

  reconfigureUtilities(self.config())
  self.onReconfigure(newConfig => reconfigureUtilities(newConfig))
  sys.addShutdownHook(() => _scheduler.shutdown())

  /**
    * Creates a new composite Filter by looking up the provided key on Kamon's configuration. All inputs matching any of
    * the include filters and none of the exclude filters will be accepted. The configuration is expected to have the
    * following structure:
    *
    * config {
    *   includes = [ "some/pattern", "regex:some[0-9]" ]
    *   excludes = [ ]
    * }
    *
    * By default, the patterns are treated as Glob patterns but users can explicitly configure the pattern type by
    * prefixing the pattern with either "glob:" or "regex:". If any of the elements are missing they will be considered
    * empty.
    */
  def filter(configKey: String): Filter =
    _filters.atomicGetOrElseUpdate(configKey, Filter.from(configKey))

  /**
    * Kamon's Clock implementation.
    */
  def clock(): Clock =
    _clock

  /**
    * Scheduler to be used for Kamon-related tasks like updating range samplers.
    */
  def scheduler(): ScheduledExecutorService =
    _scheduler

  private def reconfigureUtilities(config: Config): Unit = {
    _filters.clear()
    _scheduler match {
      case stpe: ScheduledThreadPoolExecutor =>
        val newPoolSize = config.getInt("kamon.scheduler-pool-size")
        stpe.setCorePoolSize(newPoolSize)

      case _ => // cannot change the pool size on other unknown types.
    }
  }
}
