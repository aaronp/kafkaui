package franz.users

object Permissions {

  object roles {
    // the ability to modify roles
    val Read = "GET:/rbac/roles"
    val Update = "POST:/rbac/roles"
    val Remove = "DELETE:/rbac/roles"
  }

  object userRoles {
    val AssignUserToRolePrefix = "POST:/rbac/userrole"
    val ListUseRolePrefix = "GET:/rbac/userrole"

    def canAssign(roles: String*): JWT = {
      canAssign(roles.toSet)
    }
    // TODO - design more robust permission model
    def canAssign(roles: Set[String]): JWT = {
      //s"$AssignUserToRolePrefix:${roles.sorted.mkString("[", ";", "]")}"
      AssignUserToRolePrefix
    }

    def canList(forUser: String) = s"${ListUseRolePrefix}/$forUser"
  }

}
