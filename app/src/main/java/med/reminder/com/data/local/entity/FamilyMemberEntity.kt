package med.reminder.com.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val age: Int,
    val relation: String, // parent, child, spouse, sibling, other
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
