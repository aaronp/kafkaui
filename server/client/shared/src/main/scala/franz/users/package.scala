package franz

package object users {

  type User = Claims

  type JWT = String
  type WebUser = (JWT, User)

  private[users] def swap(multiMap : Map[String, Set[String]]): Map[String, Set[String]] = {
    multiMap.foldLeft(Map.empty[String, Set[String]]) {
      case (map, (key1, values)) =>
        values.foldLeft(map) {
          case (nestedMap, key2) =>
            val newValues = nestedMap.getOrElse(key2, Set.empty[String]) + key1
            nestedMap.updated(key2, newValues)
        }
    }
  }

}
