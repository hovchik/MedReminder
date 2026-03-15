package com.medreminder.data.local

import androidx.room.*
import com.medreminder.data.local.entity.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMember(member: FamilyMemberEntity): Long

    @Update
    suspend fun updateFamilyMember(member: FamilyMemberEntity)

    @Delete
    suspend fun deleteFamilyMember(member: FamilyMemberEntity)

    @Query("SELECT * FROM family_members WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveFamilyMembers(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE id = :id")
    suspend fun getFamilyMemberById(id: Long): FamilyMemberEntity?

    @Query("SELECT * FROM family_members")
    suspend fun getAllFamilyMembersSync(): List<FamilyMemberEntity>

    @Query("DELETE FROM family_members")
    suspend fun deleteAllFamilyMembers()
}
