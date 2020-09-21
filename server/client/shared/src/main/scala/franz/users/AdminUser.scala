package franz.users

/**
 * Create a separate explicit auth route for admin users to guard against admin and normal routes
 *
 * @param claims the underlying user
 */
case class AdminUser(jwt : JWT, claims: Claims) {
  def userName = claims.name
  def userId = claims.userId
  def roles = claims.roles
}
