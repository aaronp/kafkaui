package franz.client.js

import donovan.json.JPath
import io.circe.Json

object JsonAsString {

  def atPath(path : String)(value : Json): String = {
    JPath(path).apply(value).fold(apply(value))(apply)
  }

  def apply(value : Json): String = {
    value.fold(
      "",
      _ => value.noSpaces,
      _ => value.noSpaces,
      identity,
      _ => value.noSpaces,
      _ => value.noSpaces
    )
  }
}
