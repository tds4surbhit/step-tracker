package org.example.steptracker.device;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.device.dto.DeviceRegisterRequest;
import org.example.steptracker.device.dto.DeviceResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceResponse register(UUID userId, DeviceRegisterRequest request) {
        Device device = new Device(userId, request.platform(), request.healthSource());
        device = deviceRepository.save(device);
        return new DeviceResponse(device.getId());
    }
}
