package franz.data.crud

import org.http4s.dsl.Http4sDsl

package object routes {

  /**
   * Typically 'GET' requests will specify a ?id=xyz as the means to retrieve a specific record. The name of the query
   * parameter (in this case 'id') is arbitrary, but we choose 'id' to be the default
   */
  val DefaultQueryParam = "id"

  def join[F[_]](path: Seq[String], dsl: Http4sDsl[F] = Http4sDsl[F]): dsl.Path = {
    import dsl._
    path.foldLeft(Root: Path)(_ / _)
  }

}
