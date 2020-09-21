package franz.firestore

import franz.data.crud.CrudServicesAnyCollection

object CrudServicesAnyCollectionFirestore {

  def apply(): CrudServicesAnyCollection[FS] = {
    CrudServicesAnyCollection(
      FSInsert(),
      FSRead(),
      FSListCollections(),
      FSDelete()
    )
  }
}
