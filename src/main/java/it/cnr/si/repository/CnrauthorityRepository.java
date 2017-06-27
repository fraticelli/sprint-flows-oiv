package it.cnr.si.repository;

import it.cnr.si.domain.Cnrauthority;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for the Cnrauthority entity.
 */
public interface CnrauthorityRepository extends JpaRepository<Cnrauthority,Long> {

    @Query("select distinct cnrauthority from Cnrauthority cnrauthority left join fetch cnrauthority.cnrauthorityparents")
    public List<Cnrauthority> findAllWithEagerRelationships();

    @Query("select cnrauthority from Cnrauthority cnrauthority left join fetch cnrauthority.cnrauthorityparents where cnrauthority.id =:id")
    public Cnrauthority findOneWithEagerRelationships(@Param("id") Long id);

}
