#!/bin/bash
# [무엇] EC2(Ubuntu) 서버 첫 준비 스크립트
# [역할] ① 보조 메모리(스왑) 2GB 추가 (메모리 1~2GB 서버에서 빌드·MySQL이 메모리 부족으로 죽지 않게)
#        ② Docker와 Docker Compose 설치
#        ③ ubuntu 사용자가 sudo 없이 docker 명령을 쓸 수 있게 권한 추가
# [누가 쓰나] 서버에 처음 접속했을 때 한 번만 실행: bash scripts/deploy/setup-server.sh
set -e

# ① 스왑 2GB (이미 있으면 건너뜀)
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅 후에도 유지
fi

# ② Docker 설치 (Docker 공식 설치 스크립트)
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
fi

# ③ ubuntu 사용자를 docker 그룹에 추가 (적용은 다시 접속한 뒤부터)
sudo usermod -aG docker "$USER"

echo "준비 완료: 접속을 끊었다가 다시 접속한 뒤 docker 명령을 쓰세요."
free -h
docker --version
