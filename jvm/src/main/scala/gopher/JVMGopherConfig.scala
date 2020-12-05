package gopher

import java.util.concurrent.ExecutorService


case class JVMGopherConfig(
    controlExecutor: ExecutorService,
    taskExecutor: ExecutorService
) extends GopherConfig
