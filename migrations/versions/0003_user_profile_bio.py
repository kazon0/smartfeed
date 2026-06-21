"""add user profile bio

Revision ID: 0003_user_profile_bio
Revises: 0002_conversation_sync_fields
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "0003_user_profile_bio"
down_revision: str | Sequence[str] | None = "0002_conversation_sync_fields"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("bio", sa.Text(), nullable=False, server_default=""),
    )


def downgrade() -> None:
    op.drop_column("users", "bio")
