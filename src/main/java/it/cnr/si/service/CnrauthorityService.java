package it.cnr.si.service;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.cnr.si.domain.Authority;
import it.cnr.si.domain.Cnrauthority;
import it.cnr.si.repository.AuthorityRepository;
import it.cnr.si.repository.CnrauthorityRepository;

/**
 * Service Implementation for managing Cnrauthority.
 */
@Service
@Transactional
public class CnrauthorityService {

    private final Logger log = LoggerFactory.getLogger(CnrauthorityService.class);

    @Inject
    private CnrauthorityRepository cnrauthorityRepository;
    @Inject
    private AuthorityRepository authorityRepository;


    /**
     * Save a cnrauthority.
     *
     * @param cnrauthority the entity to save
     * @return the persisted entity
     */
    public Cnrauthority save(Cnrauthority cnrauthority) {
        log.debug("Request to save Cnrauthority : {}", cnrauthority);
        Authority authority = new Authority();
        authority.setName(cnrauthority.getName());
        Authority savedAuthority = authorityRepository.save(authority);
        cnrauthority.setAuthority(savedAuthority);
        Cnrauthority result = cnrauthorityRepository.save(cnrauthority);
        return result;
    }

    /**
     *  Get all the cnrauthorities.
     *
     *  @param pageable the pagination information
     *  @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<Cnrauthority> findAll(Pageable pageable) {
        log.debug("Request to get all Cnrauthorities");
        Page<Cnrauthority> result = cnrauthorityRepository.findAll(pageable);
        return result;
    }

    /**
     *  Get one cnrauthority by id.
     *
     *  @param id the id of the entity
     *  @return the entity
     */
    @Transactional(readOnly = true)
    public Cnrauthority findOne(Long id) {
        log.debug("Request to get Cnrauthority : {}", id);
        Cnrauthority cnrauthority = cnrauthorityRepository.findOne(id);
        return cnrauthority;
    }

    /**
     *  Delete the  cnrauthority by id.
     *
     *  @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete Cnrauthority : {}", id);
        cnrauthorityRepository.delete(id);
    }
}