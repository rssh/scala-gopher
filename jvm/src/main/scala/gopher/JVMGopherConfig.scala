package gopher

import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService


case class JVMGopherConfig(
    controlExecutor: ExecutorService,
    taskExecutor: ExecutorService
) extends GopherConfig
