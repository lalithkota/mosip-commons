package io.mosip.kernel.uingenerator.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.mosip.kernel.uingenerator.entity.UinEntity;

/**
 * Repository having function to count free uins and find an unused uin
 * 
 * @author Dharmesh Khandelwal
 * @since 1.0.0
 *
 */
@Repository
public interface UinRepository extends JpaRepository<UinEntity, String> {

	/**
	 * Finds the number of free uins,
	 * 
	 * @param status status of the uin
	 * 
	 * @return the number of free uins
	 */
	public long countByStatus(String status);

	/**
	 * Finds multiple unused uins for update
	 * 
	 * @param status status of the uin
	 * @param count number of uins required
	 * 
	 * @return uins
	 */
	@Query(value = "select uu.uin, uu.cr_by, uu.cr_dtimes, uu.del_dtimes, uu.is_deleted, uu.upd_by, uu.upd_dtimes, uu.uin_status from kernel.uin uu where uu.uin_status=:status limit :count FOR UPDATE", nativeQuery = true)
	public List<UinEntity> findMultipleByStatusForUpdate(@Param("status") String status, @Param("count") int count);

	/**
	 * find a UIN in pool
	 * 
	 * @param uin pass uin as param
	 * 
	 * @return an unused uin
	 */
	public UinEntity findByUin(String uin);

	@Modifying
	@Query(
		value = (
			"UPDATE kernel.uin " +
			"SET uin_status=:status, " +
			"upd_by=:contextUser, " +
			"upd_dtimes=:uptimes " +
			"where uin in :uins and uin_status=:searchStatus " +
			"returning " +
			"uin, " +
			"cr_by, " +
			"cr_dtimes, " +
			"del_dtimes, " +
			"is_deleted, " +
			"upd_by, " +
			"upd_dtimes,  " +
			"uin_status"),
		nativeQuery = true
	)
	public List<UinEntity> updateStatus(
		@Param("status") String status,
		@Param("contextUser") String contextUser,
		@Param("uptimes") LocalDateTime uptimes,
		@Param("searchStatus") String searchStatus,
		@Param("uins") Collection<String> uins);
	
	@Query(value = "select uu.uin, uu.cr_by, uu.cr_dtimes, uu.del_dtimes, uu.is_deleted, uu.upd_by, uu.upd_dtimes, uu.uin_status from kernel.uin uu where uu.uin_status=?", nativeQuery = true)
	public List<UinEntity> findByStatus(String status);
	
	long countByStatusAndIsDeletedFalse(String status);
}