package franz.users

import cats.{Applicative, Functor, ~>}

/**
 * A representation of summat which can check access for a permission
 *
 * @tparam F
 */
trait PermissionPredicate[F[_]] {
  self =>
  def mapK[G[_]](implicit ev: F ~> G, fncF: Functor[F]) = new PermissionPredicate[G] {
    override def isPermitted(userRoles: Set[String], permission: String)(implicit fnc: Functor[G]): G[Boolean] = {
      ev(self.isPermitted(userRoles, permission))
    }
  }

  def isPermitted(user: User, permission: String)(implicit fnc: Functor[F]): F[Boolean] = {
    isPermitted(user.roles, permission)
  }

  def isPermitted(userRoles: Set[String], permission: String)(implicit fnc: Functor[F]): F[Boolean]
}

object PermissionPredicate {

  def permitAll[F[_] : Applicative]: PermissionPredicate[F] = lift[F] {
    case _ => true
  }

  def lift[F[_] : Applicative](thunk: (Set[String], String) => Boolean): PermissionPredicate[F] = {
    liftF {
      case (roles, perm) => Applicative[F].pure(thunk(roles, perm))
    }
  }

  def liftF[F[_]](thunk: (Set[String], String) => F[Boolean]): PermissionPredicate[F] = new PermissionPredicate[F] {
    override def isPermitted(userRoles: Set[String], permission: String)(implicit fnc: Functor[F]): F[Boolean] = {
      thunk(userRoles, permission)
    }
  }
}
