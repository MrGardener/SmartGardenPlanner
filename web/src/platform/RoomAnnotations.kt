package androidx.room

import kotlin.reflect.KClass

// The shared entity classes carry Room (Android database) annotations. In the browser they have no effect;
// these empty declarations only let the same source files compile.
annotation class Entity(val tableName: String = "", val foreignKeys: Array<ForeignKey> = [], val indices: Array<Index> = [], val primaryKeys: Array<String> = [])
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")
annotation class ForeignKey(val entity: KClass<*>, val parentColumns: Array<String>, val childColumns: Array<String>, val onDelete: Int = 0) {
    companion object { const val CASCADE = 5; const val RESTRICT = 1 }
}
annotation class Index(vararg val value: String)
