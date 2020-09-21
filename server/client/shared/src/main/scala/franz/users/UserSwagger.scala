package franz.users


import franz.data.{QueryRange, VersionedRecord}
import franz.rest.Swagger._
import franz.users.Roles.RolePermissions
import franz.users.UserRoles.AssociateRolesWithUser
import io.circe.Json
import io.circe.syntax._

/**
 * A simple declaration shared between clients (and server) for the User REST routes
 */
object UserSwagger {
  val Rest: UserSwagger = UserSwagger("/rest")

  val RBAC = "rbac"
  val UserRole = "userrole"
  val Roles = "roles"
  val UserLogin = "login"
  val UserLogout = "logout"
  val Users = "users"
  val Status = "status"
}
case class UserSwagger(restPrefix: String = "") {
  import UserSwagger._

  def login(request: Login.Request): PostRequest = PostRequest(s"/$UserLogin", request.asJson)

  def logout(userToken : String): PostRequest = PostRequest(s"/$UserLogout", userToken.asJson)

  /**
   * See the [[RolesRoute]]
   */
  object roles {
    def listRolesRequest(range : QueryRange) = {
      GetRequest(s"$restPrefix/$RBAC/$Roles?from=${range.from}&limit=${range.limit}")
    }

    def createRequest(newRole: VersionedRecord[RolePermissions]) = PostRequest(s"$restPrefix/$RBAC/$Roles", newRole.asJson)

    def removeRole(roleToRemove: String) = DeleteRequest(s"$restPrefix/$RBAC/$Roles/$roleToRemove")
  }

  object userRoles {
    def associateRoles(setRequest: VersionedRecord[AssociateRolesWithUser]) = PostRequest(s"$restPrefix/$RBAC/$UserRole", setRequest.asJson)

    def listRoles(forUser: String) = GetRequest(s"$restPrefix/$RBAC/$UserRole/$forUser")
  }

  object users {
    def createRequest(newUser: CreateUser.Request): PostRequest = PostRequest(s"/$Users", newUser.asJson)

    def createRequest(email: String, pwd: String): PostRequest = createRequest(CreateUser.Request(email, email, pwd))

    def status = GetRequest(s"$restPrefix/$Status")
  }

}
