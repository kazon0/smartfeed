"""add conversation sync fields

Revision ID: 0002_conversation_sync_fields
Revises: 0001_cloud_schema
"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "0002_conversation_sync_fields"
down_revision: str | Sequence[str] | None = "0001_cloud_schema"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "conversations",
        sa.Column("summary", sa.Text(), nullable=False, server_default=""),
    )
    op.add_column(
        "conversations",
        sa.Column("status", sa.String(length=40), nullable=False, server_default=""),
    )
    op.add_column(
        "conversations",
        sa.Column("stored_chunks", sa.Integer(), nullable=False, server_default="0"),
    )
    op.add_column(
        "messages",
        sa.Column("payload", sa.Text(), nullable=False, server_default="{}"),
    )


def downgrade() -> None:
    op.drop_column("messages", "payload")
    op.drop_column("conversations", "stored_chunks")
    op.drop_column("conversations", "status")
    op.drop_column("conversations", "summary")
