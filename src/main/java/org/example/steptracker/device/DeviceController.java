package org.example.steptracker.device;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.CurrentUser;
import org.example.steptracker.device.dto.DeviceRegisterRequest;
import org.example.steptracker.device.dto.DeviceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse register(@Valid @RequestBody DeviceRegisterRequest request) {
        return deviceService.register(CurrentUser.id(), request);
    }
}
