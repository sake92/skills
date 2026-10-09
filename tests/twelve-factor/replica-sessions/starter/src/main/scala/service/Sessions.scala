package service

import scala.collection.mutable

trait SessionStore:
  def put(token: String, user: String): Unit
  def get(token: String): Option[String]
  def remove(token: String): Unit

final class SharedStore extends SessionStore:
  private val entries = mutable.Map.empty[String, String]
  def put(token: String, user: String): Unit = entries.update(token, user)
  def get(token: String): Option[String] = entries.get(token)
  def remove(token: String): Unit =
    entries.remove(token); ()

final class Sessions(store: SessionStore, emit: String => Unit):
  private val local = mutable.Map.empty[String, String]
  def signIn(token: String, user: String, requestId: String): Unit =
    local.update(token, user)
    emit(s"Signed in $user with token $token")
  def currentUser(token: String): Option[String] = local.get(token)
  def signOut(token: String, requestId: String): Unit =
    local.remove(token)
    emit(s"Signed out token $token")
