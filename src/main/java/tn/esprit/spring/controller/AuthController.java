package tn.esprit.spring.controller;

import net.bytebuddy.utility.RandomString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.spring.Entity.ERole;
import tn.esprit.spring.Entity.Role;
import tn.esprit.spring.Entity.User;
import tn.esprit.spring.Payload.request.LoginRequest;
import tn.esprit.spring.Payload.request.SignupRequest;
import tn.esprit.spring.Payload.response.JwtResponse;
import tn.esprit.spring.Payload.response.MessageResponse;
import tn.esprit.spring.Repository.IDepratmentRepository;
import tn.esprit.spring.Repository.IMembresOfCompany;
import tn.esprit.spring.Repository.IRoleRepository;
import tn.esprit.spring.Repository.IUserRepository;
import tn.esprit.spring.Security.jwt.JwtUtils;
import tn.esprit.spring.Service.UserDetails;
import tn.esprit.spring.Service.UserService;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/auth")
public class AuthController {
	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	IUserRepository userRepository;

	@Autowired
	IRoleRepository roleRepository;

	@Autowired
	IDepratmentRepository depratmentRepository;
	@Autowired
	IMembresOfCompany membresOfCompany;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JwtUtils jwtUtils;

	@Autowired
	UserService userService;

	@PostMapping("/signin")
	public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

		SecurityContextHolder.getContext().setAuthentication(authentication);
		String jwt = jwtUtils.generateJwtToken(authentication);
		
		UserDetails userDetails = (UserDetails) authentication.getPrincipal();
		List<String> roles = userDetails.getAuthorities().stream()
				.map(item -> item.getAuthority())
				.collect(Collectors.toList());

		return ResponseEntity.ok(new JwtResponse(jwt, 
												 userDetails.getId(), 
												 userDetails.getUsername(), 
												 userDetails.getEmail(), 
												 roles));
	}

	@PostMapping("/signup")
	public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest, HttpServletRequest request) throws MessagingException, UnsupportedEncodingException {

		if (signUpRequest.getUsername().length()<5 && signUpRequest.getUsername().length()>20  ){
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: Username must be between 4 and 20 lettres!"));
		}

		if (userRepository.existsByUserName(signUpRequest.getUsername())) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: Username is already taken!"));
		}


		if (signUpRequest.getNid().length()<=7 && signUpRequest.getNid().length()>12  ) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: National ID must be between 8 and 10 characters!"));
		}

		if (!(membresOfCompany.existsByNid(signUpRequest.getNid()))) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: National ID isn't registered in our DATABASE!"));
		}

		if (userRepository.existsByNid(signUpRequest.getNid())) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: National ID is already taken!"));
		}

		String regexEmail ="^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$";
		Pattern pattern = Pattern.compile(regexEmail);
		Matcher matcher = pattern.matcher(signUpRequest.getEmail());
		if (!matcher.matches()  ) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: Email is invalid!"));
		}

		if (userRepository.existsByEmail(signUpRequest.getEmail())) {
			return ResponseEntity
					.badRequest()
					.body(new MessageResponse("Error: Email is already in use!"));
		}



		// Create new user's account
		User user = new User(signUpRequest.getUsername(), 
							 signUpRequest.getEmail(),
							 encoder.encode(signUpRequest.getPassword()));

		Set<String> strRoles = signUpRequest.getRole();
		Set<Role> roles = new HashSet<>();

		if (strRoles == null) {
			Role userRole = roleRepository.findByName(ERole.ROLE_EMPLOYEE)
					.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
			roles.add(userRole);
		} else {
			strRoles.forEach(role -> {
				switch (role) {
				case "admin":
					Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
							.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
					roles.add(adminRole);

					break;
				case "employee":
					Role employeeRole = roleRepository.findByName(ERole.ROLE_EMPLOYEE)
							.orElseThrow(() -> new RuntimeException("Error: Role is not found."));
					roles.add(employeeRole);

					break;
				}
			});
		}

		user.setRoles(roles);
		String randomCode = RandomString.make(64);
		user.setVerificationCode(randomCode);
		user.setEnabled(false);
		userRepository.save(user);
		userService.sendVerificationEmail(user, getSiteURL(request));

		return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
	}



	@GetMapping("/verify")
	public String verifyUser(@Param("code") String code) {
		if (userService.verify(code)) {
			return "verify_success";
		} else {
			return "verify_fail";
		}
	}
	private String getSiteURL(HttpServletRequest request) {
		String siteURL = request.getRequestURL().toString();
		return siteURL.replace(request.getServletPath(), "/auth");
	}


}