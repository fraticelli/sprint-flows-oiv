package it.cnr.si.security;

import it.cnr.si.domain.User;
import it.cnr.si.flows.ng.utils.Utils;
import it.cnr.si.repository.UserRepository;
import it.cnr.si.service.FlowsUserService;
import it.cnr.si.service.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Authenticate a user from the database.
 */
@Component("flowsUserDetailsService")
@Primary
public class FlowsUserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

	private final Logger log = LoggerFactory.getLogger(FlowsUserDetailsService.class);

	@Inject
	private UserRepository userRepository;
	@Autowired(required = false)
	private LdapUserDetailsService ldapUserDetailsService;
	@Inject
	private MembershipService membershipService;
	@Inject
	private Utils utils;
	@Inject
	private FlowsUserService flowsUserService;


	@Override
	@Transactional
	//la cache "user" attivata solo per il profilo cnr perchè in oiv abbiamo pochi utenti e la cache fa casini con la creazione dei nuovi utenti
	@Cacheable(value = "user", key = "#login", condition = "@utils.isProfileActive(\"cnr\")")
	public UserDetails loadUserByUsername(final String login) {
		UserDetails userDetails = null;

		log.debug("Loading User {}", login);
		String lowercaseLogin = login.toLowerCase(Locale.ENGLISH);
		Optional<User> userFromDatabase = userRepository.findOneByLogin(lowercaseLogin);
		if (userFromDatabase.isPresent()) {
			userDetails = userFromDatabase.map(user -> {
				if (!user.getActivated()) {
					throw new UserNotActivatedException("User " + lowercaseLogin + " was not activated");
				}
				List<GrantedAuthority> grantedAuthorities = user.getAuthorities().stream()
						.map(authority -> new SimpleGrantedAuthority(authority.getName()))
						.collect(Collectors.toList());
				grantedAuthorities.addAll(flowsUserService.getGroupsForUser(lowercaseLogin, new PageRequest(1, 100)).stream()
				.map(group -> new SimpleGrantedAuthority(group.getCnrgroup().getName()))
				.collect(Collectors.toList()));

				return new org.springframework.security.core.userdetails.User(lowercaseLogin,
				                                                              user.getPassword(),
				                                                              grantedAuthorities);
			}).orElseGet(null);
		} else {
			userDetails = Optional.ofNullable(ldapUserDetailsService)
					.map(ldapUserDetailsService1 -> ldapUserDetailsService1.loadUserByUsername(login))
					.orElse(null);
		}

		if (userDetails == null)
			throw new UsernameNotFoundException("User " + lowercaseLogin + " was not found in the " +
					                                    "database or LDAP");
		else
			return userDetails;
	}
}
