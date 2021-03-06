package com.landoop.streamreactor.connect.hive.sink

import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.sink.SinkRecord

import scala.collection.JavaConverters._

object ValueConverter {
  def apply(record: SinkRecord): Struct = record.value match {
    case struct: Struct => StructValueConverter.convert(struct)
    case map: Map[_, _] => MapValueConverter.convert(map)
    case map: java.util.Map[_, _] => MapValueConverter.convert(map.asScala.toMap)
    case string: String => StringValueConverter.convert(string)
    case other => sys.error(s"Unsupported record $other:${other.getClass.getCanonicalName}")
  }
}

trait ValueConverter[T] {
  def convert(value: T): Struct
}

object StructValueConverter extends ValueConverter[Struct] {
  override def convert(struct: Struct): Struct = struct
}

object MapValueConverter extends ValueConverter[Map[_, _]] {
  def convertValue(value: Any, key: String, builder: SchemaBuilder): Any = {
    value match {
      case s: String =>
        builder.field(key, Schema.OPTIONAL_STRING_SCHEMA)
        s
      case l: Long =>
        builder.field(key, Schema.OPTIONAL_INT64_SCHEMA)
        l
      case i: Int =>
        builder.field(key, Schema.OPTIONAL_INT64_SCHEMA)
        i.toLong
      case b: Boolean =>
        builder.field(key, Schema.OPTIONAL_BOOLEAN_SCHEMA)
        b
      case f: Float =>
        builder.field(key, Schema.OPTIONAL_FLOAT64_SCHEMA)
        f.toDouble
      case d: Double =>
        builder.field(key, Schema.OPTIONAL_FLOAT64_SCHEMA)
        d
      case innerMap: java.util.Map[_, _] =>
        val innerStruct = convert(innerMap.asScala.toMap, true)
        builder.field(key, innerStruct.schema())
        innerStruct

      case innerMap: Map[_, _] =>
        val innerStruct = convert(innerMap, true)
        builder.field(key, innerStruct.schema())
        innerStruct
    }
  }

  def convert(map: Map[_, _], optional: Boolean) = {
    val builder = SchemaBuilder.struct()
    val values = map.map { case (k, v) =>
      val key = k.toString
      val value = convertValue(v, key, builder)
      key -> value
    }.toList
    if (optional) builder.optional()
    val schema = builder.build
    val struct = new Struct(schema)
    values.foreach { case (key, value) =>
      struct.put(key.toString, value)
    }
    struct
  }
  override def convert(map: Map[_, _]): Struct = convert(map, false)
}

object StringValueConverter extends ValueConverter[String] {
  override def convert(string: String): Struct = {
    val schema = SchemaBuilder.struct().field("a", Schema.OPTIONAL_STRING_SCHEMA).name("struct").build()
    new Struct(schema).put("a", string)
  }
}