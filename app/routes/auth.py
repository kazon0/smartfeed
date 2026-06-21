from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from fastapi import status
from fastapi.security import HTTPAuthorizationCredentials
from fastapi.security import HTTPBearer
from sqlalchemy.orm import Session

from app.db.dependencies import get_db
from app.models.user import User
from app.schemas.auth import AuthResponse
from app.schemas.auth import LoginRequest
from app.schemas.auth import RegisterRequest
from app.schemas.auth import UpdateUserRequest
from app.schemas.auth import UserResponse
from app.security.tokens import create_access_token
from app.security.tokens import decode_access_token
from app.services.auth_service import AuthService
from app.services.auth_service import EmailAlreadyRegisteredError

router = APIRouter(prefix="/auth", tags=["auth"])
bearer = HTTPBearer(auto_error=False)


@router.post("/register", response_model=AuthResponse, status_code=status.HTTP_201_CREATED)
def register(request: RegisterRequest, db: Session = Depends(get_db)):
    try:
        user = AuthService().register(
            db,
            email=str(request.email),
            password=request.password,
            display_name=request.display_name,
        )
    except EmailAlreadyRegisteredError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Email is already registered.",
        ) from exc
    return _auth_response(user)


@router.post("/login", response_model=AuthResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    user = AuthService().authenticate(
        db,
        email=str(request.email),
        password=request.password,
    )
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return _auth_response(user)


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise _unauthorized()
    user_id = decode_access_token(credentials.credentials)
    if user_id is None:
        raise _unauthorized()
    user = AuthService().get_user(db, user_id)
    if user is None:
        raise _unauthorized()
    return user


@router.get("/me", response_model=UserResponse)
def me(user: User = Depends(get_current_user)):
    return user


@router.patch("/me", response_model=UserResponse)
def update_me(
    request: UpdateUserRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return AuthService().update_profile(
        db,
        user,
        display_name=request.display_name,
        bio=request.bio,
    )


def _auth_response(user: User) -> AuthResponse:
    return AuthResponse(
        access_token=create_access_token(user.id),
        user=UserResponse.model_validate(user),
    )


def _unauthorized() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or missing access token.",
        headers={"WWW-Authenticate": "Bearer"},
    )
