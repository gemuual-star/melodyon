package operation

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/gemuual-star/melodyon/windows-installer/internal/githubrelease"
	"github.com/gemuual-star/melodyon/windows-installer/internal/installer"
	"github.com/gemuual-star/melodyon/windows-installer/internal/payload"
)

type Stage string

const (
	StageResolving   Stage = "Finding release"
	StageDownloading Stage = "Downloading Melodyon Desktop"
	StageVerifying   Stage = "Verifying Melodyon Desktop"
	StageWaiting     Stage = "Waiting for Melodyon Desktop to close"
	StageInstalling  Stage = "Installing Melodyon Desktop"
	StageRegistering Stage = "Registering shortcuts"
	StageLaunching   Stage = "Launching Melodyon Desktop"
	StageComplete    Stage = "Installation complete"
)

type Event struct {
	Stage      Stage
	Downloaded int64
	Total      int64
}

type ArtifactDownloader interface {
	Download(ctx context.Context, asset githubrelease.Asset, destination string, progress githubrelease.Progress) error
}

type Registrar interface {
	Register(root, version string, desktopShortcut bool) error
}

type Dependencies struct {
	ResolveAsset  func(ctx context.Context) (githubrelease.Asset, error)
	Downloader    ArtifactDownloader
	Registrar     Registrar
	EnsureStopped func(context.Context) error
	Launch        func(path string) error
}

type Request struct {
	Version         string
	InstallRoot     string
	ManagerSource   string
	DesktopShortcut *bool
	LaunchAfter     bool
}

func Run(ctx context.Context, request Request, dependencies Dependencies, report func(Event)) error {
	if dependencies.ResolveAsset == nil || dependencies.Downloader == nil || dependencies.Registrar == nil {
		return fmt.Errorf("operation dependencies are incomplete")
	}
	if request.LaunchAfter && dependencies.Launch == nil {
		return fmt.Errorf("launch dependency is missing")
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	reportEvent(report, Event{Stage: StageResolving})
	asset, err := dependencies.ResolveAsset(ctx)
	if err != nil {
		return err
	}
	if asset.Name == "" || filepath.Base(asset.Name) != asset.Name || strings.ContainsAny(asset.Name, `\/:`) || asset.Name == "." || asset.Name == ".." {
		return fmt.Errorf("invalid archive name %q", asset.Name)
	}
	temporaryDirectory, err := os.MkdirTemp("", "melodyon-manager-")
	if err != nil {
		return fmt.Errorf("create download directory: %w", err)
	}
	defer os.RemoveAll(temporaryDirectory)
	archivePath := filepath.Join(temporaryDirectory, asset.Name)
	reportEvent(report, Event{Stage: StageDownloading, Total: asset.Size})
	if err := dependencies.Downloader.Download(ctx, asset, archivePath, func(downloaded, total int64) {
		reportEvent(report, Event{Stage: StageDownloading, Downloaded: downloaded, Total: total})
	}); err != nil {
		return err
	}
	reportEvent(report, Event{Stage: StageVerifying})
	archive, _, err := payload.Open(archivePath, request.Version)
	if err != nil {
		return err
	}
	defer archive.Close()
	if err := archive.VerifyContext(ctx); err != nil {
		return err
	}

	if dependencies.EnsureStopped != nil {
		reportEvent(report, Event{Stage: StageWaiting})
		if err := ctx.Err(); err != nil {
			return err
		}
		if err := dependencies.EnsureStopped(ctx); err != nil {
			return err
		}
	}
	if err := ctx.Err(); err != nil {
		return err
	}

	reportEvent(report, Event{Stage: StageInstalling})
	marker, err := installer.Apply(installer.ApplyRequest{
		Archive:         archive,
		InstallRoot:     request.InstallRoot,
		ManagerSource:   request.ManagerSource,
		ExpectedVersion: request.Version,
		DesktopShortcut: request.DesktopShortcut,
	})
	if err != nil {
		return err
	}
	reportEvent(report, Event{Stage: StageRegistering})
	if err := dependencies.Registrar.Register(request.InstallRoot, request.Version, marker.DesktopShortcut); err != nil {
		return fmt.Errorf("register installation: %w", err)
	}
	if request.LaunchAfter {
		reportEvent(report, Event{Stage: StageLaunching})
		launcher := filepath.Join(request.InstallRoot, installer.AppDirectoryName, "Melodyon.exe")
		if err := dependencies.Launch(launcher); err != nil {
			return fmt.Errorf("launch Melodyon Desktop: %w", err)
		}
	}
	reportEvent(report, Event{Stage: StageComplete})
	return nil
}

func reportEvent(report func(Event), event Event) {
	if report != nil {
		report(event)
	}
}
