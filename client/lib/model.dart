class PeekRequest {
  PeekRequest(this.topics, this.fromOffset, this.limit, this.partitions);

  Set<String> topics;
  int fromOffset;
  int limit;
  Set<int> partitions;

  Map<String, Object> get asJson {
    return {
      'topics': topics.toList(),
      'fromOffset': fromOffset,
      'limit': limit,
      'partitions': partitions.toList(),
    };
  }
}

class Record {
  Record(
      this.topic,
      this.offset,
      this.leaderEpoch,
      this.partition,
      this.serializedKeySize,
      this.serializedValueSize,
      this.timestamp,
      this.timestampType,
      this.value,
      this.base64);

  String topic;
  int offset;
  int leaderEpoch;
  int partition;
  int serializedKeySize;
  int serializedValueSize;
  int timestamp;
  String timestampType;
  String value;
  String base64;


  static Record fromJson(Map<String, dynamic> json) {
    return Record(
        json['topic'],
        json['offset'],
        json['leaderEpoch'],
        json['partition'],
        json['serializedKeySize'],
        json['serializedValueSize'],
        json['timestamp'],
        json['timestampType'],
        json['value'],
        json['base64']);
  }

  @override
  String toString() {
    return 'Record{offset: $offset, partition: $partition}';
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Record &&
          runtimeType == other.runtimeType &&
          topic == other.topic &&
          offset == other.offset &&
          partition == other.partition;

  @override
  int get hashCode => topic.hashCode ^ offset.hashCode ^ partition.hashCode;
}

class PublishOne {
  PublishOne(
      this.topic,
      this.key,
      this.value,
      this.partition,
      this.isBase64
      );

  String topic;
  String key;
  String value;
  int partition;
  bool isBase64;

  Map<String, Object> get asJson {
    return {
      'topic': topic,
      'key': key,
      'value': value,
      'partition': partition,
      'isBase64': isBase64
    };
  }

  static PublishOne fromJson(Map<String, dynamic> json) {
    return PublishOne(
        json['topic'],
        json['key'],
        json['value'],
        json['partition'],
        json['isBase64']);
  }
}

class TopicKey {
  TopicKey(
      this.topic,
      this.partition
      );

  String topic;
  int partition;

  Map<String, Object> get asJson {
    return {
      'topic': topic,
      'partition': partition
    };
  }

  static TopicKey fromJson(Map<String, dynamic> json) {
    return TopicKey(
        json['topic'],
        json['partition']);
  }
}

class RecordMetadataResponse {
  RecordMetadataResponse(
      this.topicPartition,
      this.offset,
      this.timestamp,
      this.serializedKeySize,
      this.serializedValueSize
      );

  TopicKey topicPartition;
  int offset;
  int timestamp;
  int serializedKeySize;
  int serializedValueSize;

  Map<String, Object> get asJson {
    return {
      'topicPartition': topicPartition.asJson,
      'offset': offset,
      'timestamp': timestamp,
      'serializedKeySize': serializedKeySize,
      'serializedValueSize': serializedValueSize
    };
  }

  static RecordMetadataResponse fromJson(Map<String, dynamic> json) {
    return RecordMetadataResponse(
        TopicKey.fromJson(json['topicPartition']),
        json['offset'],
        json['timestamp'],
        json['serializedKeySize'],
        json['serializedValueSize']);
  }
}