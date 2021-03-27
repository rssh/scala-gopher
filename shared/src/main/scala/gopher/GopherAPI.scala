package gopher

import cps._

trait GopherConfig
case object DefaultGopherConfig extends GopherConfig


  
trait GopherAPI: 

  def apply[F[_]:CpsSchedulingMonad](cfg:GopherConfig = DefaultGopherConfig): Gopher[F]
          
/**
 * Shared gopehr api, which is initialized by platofrm part,
 * Primary used for cross-platforming test, you shoul initialize one of platform API
 *  behind and then run tests.
 **/
object SharedGopherAPI {

  private[this] var _api: Option[GopherAPI] = None

  def apply[F[_]:CpsSchedulingMonad](cfg:GopherConfig = DefaultGopherConfig): Gopher[F] =
    api.apply[F](cfg)



  def api: GopherAPI = 
    if (_api.isEmpty) then
      initPlatformSpecific()  
    _api.get


  private[gopher] def setApi(api: GopherAPI): Unit =
    this._api = Some(api)


  private[gopher] def initPlatformSpecific(): Unit =
    Platform.initShared()   
 
}