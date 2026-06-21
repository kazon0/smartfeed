from datetime import datetime
from typing import Annotated

from pydantic import BaseModel
from pydantic import ConfigDict
from pydantic import EmailStr
from pydantic import Field
from pydantic import StringConstraints


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(default="", max_length=120)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class UpdateUserRequest(BaseModel):
    display_name: Annotated[
        str,
        StringConstraints(strip_whitespace=True, min_length=1, max_length=120),
    ]
    bio: Annotated[
        str,
        StringConstraints(strip_whitespace=True, max_length=200),
    ] = ""


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: EmailStr
    display_name: str
    bio: str
    created_at: datetime
    updated_at: datetime


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse
