package com.copmap.auth.services;

import com.copmap.auth.payload.UserDto;

public interface UserService {

    //create user
    UserDto createUser(UserDto userDto);

    //get user by email
    UserDto getUserByEmail(String email);

    //delete user
    void deleteUser(String userId);

    //get user by id
    UserDto getUserById(String userId);

    //get all users
    Iterable<UserDto> getAllUsers();

    // user service se related __


}
