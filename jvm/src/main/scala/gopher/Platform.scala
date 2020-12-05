package gopher


object Platform:

   def initShared(): Unit =
    SharedGopherAPI.setApi(JVMGopher)