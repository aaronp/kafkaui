package franz.client.pages

/**
 * When the user clicks 'home' in the location, this is the page that's rendered
 *
 * @param nav
 */
class HomePage(nav: Nav) {

  /** let the user choose a collection
   */
  val collection = CollectionSelect {
    case (_, selected) => nav.move.toCollection(selected)
  }
  collection.refresh()

  def render = collection.renderDiv
}
