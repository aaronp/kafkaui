package franz.data.index

import franz.data.index.RecordAssociations.ByCollection
import franz.data.{CollectionName, IndexValue, ValuePath}


/**
 * This is just a [[FieldAssociation]] which has a Some of an index (FixedReferences)
 *
 * @param ourReference
 * @param value
 * @param indices
 */
case class IndexedAssociation(ourReference: ValuePath, value: IndexValue, indices: FixedReferences) {

  def references: Set[ReferenceToValue] = indices.references

  def keys: Set[ReferenceToValue.RecordKey] = references.map(_.key)
}

object IndexedAssociation {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[IndexedAssociation]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[IndexedAssociation]
}


/**
 * The reference from the matched document (e.g. either a versioned record or just some sample posted data)
 *
 * @param ourReference some input value (a versioned record or example (e.g. POSTed) data)
 * @param value        the value of 'ourReference' which returned the indices
 * @param indices      an optional IndexValue result associated w/ this value
 */
case class FieldAssociation(ourReference: ValuePath, value: IndexValue, indices: Option[IndexedValue]) {

  /**
   * remove the record w/ the given coords (e.g. a self-match)
   *
   * @return either a 'some' of FieldAssociation which doesn't contain these RecordCoords or a 'none' if this association is no longer valid (i.e. it's empty)
   */
  def without(collectionName: CollectionName, id: String, version: Int): Option[FieldAssociation] = {
    indices match {
      case Some(FixedReferences(references)) =>
        references.filterNot(_.matches(collectionName, id, version)) match {
          case remaining if remaining.isEmpty => None
          case remaining =>
            println(s"filtering from ${references.size} refs yields ${remaining.size}")
            println(remaining)
            Option(copy(indices = Option(FixedReferences(remaining))))
        }
      case _ => Option(this)
    }
  }


  override def toString = ourReference.mkString("FieldAssociation: ", ".", s" -> [$value], indices:${indices}")

  /** ALL references to the 'value'
   */
  lazy val references: Set[ReferenceToValue] = {
    indices.collect {
      case fix@FixedReferences(_) => fix.references
    }.toSet.flatten
  }

  def keys: Set[ReferenceToValue.RecordKey] = references.map(_.key)
}

object FieldAssociation {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[FieldAssociation]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[FieldAssociation]
}


/**
 *
 * @param key                 a collection/id pair
 * @param allAssociatedFields all the associations which have that key
 */
case class RecordMatch(key: ReferenceToValue.RecordKey, allAssociatedFields: List[IndexedAssociation]) {
  def references: List[ReferenceToValue] = allAssociatedFields.flatMap(_.references.filter(_.key == key))

  override def toString = {
    allAssociatedFields.mkString(s"Record '$key' Matching Indices:\n", "\n", "\n")
  }

  def score(matchWeights: MatchWeights): Double = {
    val weights = references.map { ref =>
      ref.score(matchWeights)
    }
    weights.sum
  }
}

object RecordMatch {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RecordMatch]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RecordMatch]
}


/**
 *
 * @param allAssociations the associations for a particular record
 */
case class RecordAssociations(allAssociations: List[FieldAssociation]) {

  override def toString: IndexValue = allAssociations.mkString("RecordAssociations(\n\t", "\n\t", "\n)")

  lazy val byCollection: ByCollection = ByCollection(this)

  lazy val indexedAssociations: List[IndexedAssociation] = allAssociations.collect {
    case FieldAssociation(ref, value, Some(indices: FixedReferences)) => IndexedAssociation(ref, value, indices)
  }

  def matches: List[RecordMatch] = {
    matchesByKey.map {
      case (key, refs) => RecordMatch(key, refs)
    }.toList
  }

  /**
   * @param weights a mapping of fields (jpaths) to weights
   * @return the matches/weights in descending order (i.e. highest weight first)
   */
  def bestMatches(weights: MatchWeights = MatchWeights.empty): List[(RecordMatch, Double)] = {
    matches.map(m => (m, m.score(weights))).sortBy(_._2)(Ordering.Double.TotalOrdering.reverse)
  }

  /**
   * Simply the most common shared fields
   */
  lazy val bestMatchByMostAssociations: Option[RecordMatch] = {
    if (matchesByKey.isEmpty) {
      None
    } else {
      val (key, assoc) = matchesByKey.maxBy(_._2.size)
      Some(RecordMatch(key, assoc))
    }
  }

  /**
   * A mapping of all associations for a particular key
   */
  lazy val matchesByKey: Map[ReferenceToValue.RecordKey, List[IndexedAssociation]] = {
    val byKeyEmpty = Map.empty[ReferenceToValue.RecordKey, List[IndexedAssociation]]
    indexedAssociations.foldLeft(byKeyEmpty) {
      case (outerMap, assoc: IndexedAssociation) =>
        assoc.keys.foldLeft(outerMap) {
          case (innerMap, key) =>
            val list: List[IndexedAssociation] = innerMap.getOrElse(key, Nil)
            innerMap.updated(key, assoc :: list)
        }
    }
  }
}

object RecordAssociations {

  case class ByCollection(associations: RecordAssociations) {
    val (byCollection: Map[CollectionName, ById], notIndexed: List[FieldAssociation]) = {
      associations.allAssociations.foldLeft((Map[CollectionName, ById](), List[FieldAssociation]())) {
        case ((byId, missing), fa@FieldAssociation(_, _, None)) => (byId, fa +: missing)
        case ((byId, missing), fa@FieldAssociation(_, _, Some(TooManyValues(_)))) => (byId, fa +: missing)
        case ((byId, missing), fa@FieldAssociation(ourRef, value, Some(FixedReferences(references)))) =>
          val newById = references.foldLeft(byId) {
            case (map, ref@ReferenceToValue(collection, _, _, _)) =>
              val byId = map.get(collection).fold(ById(ByIdEntry(ourRef, value, ref))) { found =>
                found(ByIdEntry(ourRef, value, ref))
              }
              map.updated(ref.collection, byId)
          }
          (newById, missing)
      }
    }

    /**
     * @return the matched collections, most occurrences first
     */
    def collections: List[CollectionName] = {
      byCollection.toList.sortBy {
        case (name, byId) => (-1 * byId.size, name)
      }.map(_._1)
    }
  }

  case class ByIdEntry(ourReference: ValuePath, value: IndexValue, reference: ReferenceToValue) {
    def id = reference.id

    def collection = reference.collection
  }

  //ourReference: ValuePath, value: IndexValue
  case class ById(collectionName: CollectionName, refsById: Map[String, List[ByIdEntry]]) {
    def size: Int = refsById.values.map(_.size).sum

    def apply(ref: ByIdEntry) = {
      require(ref.collection == collectionName)
      val list = refsById.getOrElse(ref.id, Nil)
      copy(refsById = refsById.updated(ref.id, ref :: list))
    }
  }

  object ById {
    def apply(ref: ByIdEntry): ById = {
      new ById(ref.collection, Map(ref.id -> List(ref)))
    }
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[RecordAssociations]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[RecordAssociations]
}
