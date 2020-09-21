package franz.db

import franz.db.impl.{DocSchemasTest, LatestRecordsMongoTest, VersionedRecordsMongoSnapshotTest, VersionedRecordsMongoTest}
import franz.db.services.UsersMongoTest

class GleichDBTest extends BaseGleichDBTest
  with VersionedRecordsMongoSnapshotTest
  with UsersMongoTest
  with CrudServicesAnyCollectionMongoTest
  with VersionedRecordsMongoTest
  with LatestRecordsMongoTest
  with DocSchemasTest
