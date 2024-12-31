package com.telt.controller;

import com.telt.dto.OrganizationRegisterDTO;
import com.telt.entity.auth.AuthResponse;
import com.telt.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organization")
public class OrganizationController {

    @Autowired
    OrganizationService organizationService;

    @Operation(summary = "Organizations register for placement opportunities")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully authenticated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))), @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)})
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid OrganizationRegisterDTO organizationDetails) {
        try {
            return ResponseEntity.status(HttpStatus.OK).body(organizationService.register(organizationDetails.getOrganizationDetails(), organizationDetails.getAddressInfo()));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }
    }
}
